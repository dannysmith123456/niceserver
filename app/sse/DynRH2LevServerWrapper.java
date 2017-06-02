package sse;

import com.google.common.collect.Multimap;
import db.UserDatabase;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import play.db.Database;
import util.Const;
import util.ImageData;
import util.Tuple;
import util.Utils;
import org.apache.commons.io.FileUtils;
import org.crypto.sse.remote.DynRH2LevStatelessServer;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * A wrapper around the dynamic 2lev server. This class should handle all the file operations.
 */
public class DynRH2LevServerWrapper {

    /**
     * Given the user id, fetch the data structures stored in the user directory and perform
     * the update. Should also probably store the picture files too. Depreciated.
     * @param userID
     */
    /*@SuppressWarnings("unchecked")
    public static void update(String userID, File image, String imageName, String tokenUp) throws IOException, ClassNotFoundException {
        // we'll write the file into the user's directory
        byte[] data = Files.readAllBytes(image.toPath());
        FileUtils.writeByteArrayToFile(new File(Const.USERS_DIR + userID + "/" + imageName), data);

        // now we need to update the index
        // first we fetch the index
        File dictUp = new File(Const.USERS_DIR + userID + Const.SSE_DIR + Const.DICT_UPDATE_FILE);
        Map<String, byte[]> dictionaryUpdates = (HashMap<String, byte[]>) new ObjectInputStream(new FileInputStream(dictUp)).readObject();
        Multimap<String, byte[]> token = Utils.JSONToTokenUp(tokenUp);
        DynRH2LevStatelessServer.update(dictionaryUpdates, token);

        // now we write the updated dictionaryUpdates back to the file system
        new ObjectOutputStream(new FileOutputStream(dictUp)).writeObject(dictionaryUpdates);
    }*/

    @SuppressWarnings("unchecked")
    public static void updateMultiToken(String userID, String tokenUp) throws IOException, ClassNotFoundException {
        // now we need to update the index
        // first we fetch the index
        DB db = DBMaker.fileDB(Utils.getDictionaryUpdatesFile(userID)).
                fileMmapEnable().
                fileMmapPreclearDisable().
                allocateStartSize(124*1024*1024).
                allocateIncrement(5 * 1024*1024).make();
        ConcurrentMap<String, byte[]> dictionaryUpdates = db.hashMap(Utils.getDictionaryFile(userID).getAbsolutePath(), Serializer.STRING, Serializer.BYTE_ARRAY).createOrOpen();
        /*File dictUp = Utils.getDictionaryUpdatesFile(userID);
        Map<String, byte[]> dictionaryUpdates = (HashMap<String, byte[]>) new ObjectInputStream(new FileInputStream(dictUp)).readObject();*/
        Multimap<String, byte[]> token = Utils.JSONToTokenUp(tokenUp);
        DynRH2LevStatelessServer.update(dictionaryUpdates, token);
        db.close();
        // now we write the updated dictionaryUpdates back to the file system
        //new ObjectOutputStream(new FileOutputStream(dictUp)).writeObject(dictionaryUpdates);
    }

    public static void updateMultiFiles(String userID, List<ImageData> images, Database db) throws IOException {
        UserDatabase udb = new UserDatabase(db);
        for (ImageData im : images) {
            String filename = im.getImageName();
            byte[] fullData = Files.readAllBytes(im.getFullImage().toPath());
            byte[] thumbData = Files.readAllBytes(im.getThumbnail().toPath());
            byte[] mediumData = Files.readAllBytes(im.getMedium().toPath());
            FileUtils.writeByteArrayToFile(new File(Utils.getFullImageDir(userID), filename), fullData);
            FileUtils.writeByteArrayToFile(new File(Utils.getThumbnailDir(userID), filename), thumbData);
            FileUtils.writeByteArrayToFile(new File(Utils.getMediumImageDir(userID), filename), mediumData);
            udb.insertImageTimestamp(Integer.parseInt(userID), filename);
            // garbage collect during memory intensive task to free up some memory
            fullData = null;
            thumbData = null;
            mediumData = null;
            System.gc();
        }
        udb.close(); // free resource
    }

    public static void updateMulti(String userID, List<ImageData> images, String tokenUp, Database db) throws IOException, ClassNotFoundException {
        // we'll write the files into the user's directory
        updateMultiFiles(userID, images, db);
        // now we need to update the index
        updateMultiToken(userID, tokenUp);
    }

    /**
     * Given the user id, the cmac byte array and the token 2D byte array, do an encrypted search for the files.
     * In this query, we return the thumbnails to the client.
     * @param userID the id of the user
     * @param cmac the cmac
     * @param token the search token
     * @return a list of thumbnail files that match the query, as well as a map of tags to each of the images
     */
    @SuppressWarnings("unchecked")
    public static Tuple<List<File>, Map<String, byte[]>> query(String userID, String cmac, String token, Database db, Set<String> cachedImages) throws Exception {
        // turn the cmac and token into a byte array again
        byte[] cmacReal = Utils.JSONToByteArray(cmac);
        byte[][] tokenReal = Utils.JSONToByteArray2D(token);
        byte[][] arr = null;

        DB mapdb = DBMaker.fileDB(Utils.getDictionaryUpdatesFile(userID)).
                fileMmapEnable().
                fileMmapPreclearDisable().
                allocateStartSize(124*1024*1024).
                allocateIncrement(5 * 1024*1024).make();
        ConcurrentMap<String, byte[]> dictionaryUpdates = mapdb.hashMap(Utils.getDictionaryFile(userID).getAbsolutePath(), Serializer.STRING, Serializer.BYTE_ARRAY).createOrOpen();
        // now we have to retrieve the dictionary and dictionary updates
        //File dictUp = Utils.getDictionaryUpdatesFile(userID);
        File dict = Utils.getDictionaryFile(userID);
        //Map<String, byte[]> dictionaryUpdates = (Map<String, byte[]>) new ObjectInputStream(new FileInputStream(dictUp)).readObject();
        Multimap<String, byte[]> dictionary = (Multimap<String, byte[]>) new ObjectInputStream(new FileInputStream(dict)).readObject();

        // now we make the query
        List<String> identifiers = null;
        identifiers = DynRH2LevStatelessServer.query(cmacReal, tokenReal, dictionary, arr, dictionaryUpdates);
        /*try {
            identifiers = DynRH2LevStatelessServer.query(cmacReal, tokenReal, dictionary, arr, dictionaryUpdates);
        } catch (Exception e) {
            e.printStackTrace();
            mapdb.close();
            return new Tuple<>(new ArrayList<>(), new HashMap<>());
        }*/
        mapdb.close();
        // we turn each of the outputs into files and return that
        List<File> files = new ArrayList<>();
        Map<String, byte[]> encTags = new HashMap<>();
        // get the encrypted tags for each of the identifiers
        UserDatabase udb = new UserDatabase(db);
        for (String ident : identifiers) {
            if (cachedImages.contains(ident)) {
                continue; // if this image is already in our cache, skip it
            }
            File f = new File(Const.USERS_DIR + userID + Const.THUMBNAIL_DIR +  "/" + ident);
            if (f.exists()) {
                files.add(f);
                encTags.put(ident, udb.getImageTags(userID, ident));
            }
        }
        udb.close();
        return new Tuple<>(files, encTags);
    }

    /**
     * Retrieves images by id starting the image equal to or older than the given id.
     * This is to avoid issues that may be caused by batch insertion of images, as they will all contain
     * the same timestamp since the granularity is in seconds.
     * @param userID the user to query from.
     * @param localImages the names of the images that are already on the device and that we don't want to resend
     * @param numQuery the number of images to retrieve.
     * @return a list of tuples containing files and their timestamp.
     */
    @SuppressWarnings("unchecked")
    public static Tuple<List<File>, Map<String, byte[]>> queryByTimestamp(String userID, String[] localImages, long numQuery, Database db) {
        UserDatabase udb = new UserDatabase(db);
        // we want to retrive numQuery images with timestamp less than or equal to this id
        // we can make sure not to include lastImageSent in this batch
        List<String> identifiers = udb.getImagesNotInSet(userID, new HashSet<>(Arrays.asList(localImages)), numQuery);
        List<File> files = new ArrayList<>();
        Map<String, byte[]> encTags = new HashMap<>();
        for (String ident : identifiers) {
            File f = new File(Utils.getThumbnailDir(userID), ident);
            files.add(f);
            encTags.put(ident, udb.getImageTags(userID, ident));
        }
        // free the database resource
        udb.close();
        return new Tuple<>(files, encTags);
    }

    public static void backupState(String userID, File stateBackup) throws IOException {
        // get the bytes of the encrypted state
        byte[] encState = Files.readAllBytes(stateBackup.toPath());
        File stateFile = Utils.getStateFile(userID);
        FileUtils.writeByteArrayToFile(stateFile, encState);
    }

    public static void deleteImage(String userID, String imageName, Database db) {
        File fullImage = new File(Utils.getFullImageDir(userID), imageName);
        File thumb = new File(Utils.getThumbnailDir(userID), imageName);
        fullImage.delete();
        thumb.delete();

        // delete from database
        UserDatabase udb = new UserDatabase(db);
        udb.deleteImage(userID, imageName);
        udb.close();
    }

}
