package util;

import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.TreeMultimap;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.StringEscapeUtils;
import org.crypto.sse.CryptoPrimitives;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import javax.json.Json;
import javax.json.JsonObjectBuilder;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Utility functions.
 */
public class Utils {

    private static final Gson GSON = new Gson();
    private static final char[] VALID_CHARACTERS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456879".toCharArray();

    /**
     * Takes in a byte array and encode it as a json compatible string.
     *
     * @param bytes
     * @return a string that represents the byte array.
     */
    public static String byteArrayToJSON(byte[] bytes) {
        return GSON.toJson(bytes);
    }

    /**
     * Takes in a string that represents a byte array and converts it to a real byte array
     *
     * @param bytes
     * @return a byte array
     */
    public static byte[] JSONToByteArray(String bytes) {
        return GSON.fromJson(bytes, byte[].class);
    }

    /**
     * Turns an update token (a multimap) into a json string)
     * @param tokenUp
     * @return
     */
    public static String tokenUpToJSON(Multimap<String, byte[]> tokenUp) {
        return GSON.toJson(tokenUp.asMap());
    }

    public static String byteArrayToJSON2D(byte[][] bytes) {
        return GSON.toJson(bytes);
    }

    public static byte[][] JSONToByteArray2D(String bytes) {
        return GSON.fromJson(bytes, byte[][].class);
    }

    public static Multimap<String, byte[]> JSONToTokenUp(String tokenUp) {
        String[] pairs = tokenUp.replaceAll("^\\{|\\}$", "").split(",(?![^(\\[]*[\\])])");
        // we use the lexigraphically ordered multimap so the map is history independent.
        Multimap<String, byte[]> multi = TreeMultimap.create(Ordering.natural(), Ordering.usingToString());
        for (int i = 0; i < pairs.length; i++) {
            String[] temp = pairs[i].split(":");
            // make sure to keep the string json format
            String key = StringEscapeUtils.unescapeJson(temp[0].trim().replaceAll("^\"|\"$", ""));
            String values = temp[1].trim();
            // if we split badly, join things back up
            while (!values.endsWith("]]")) {
                values += "," + pairs[++i].trim();
            }
            List<byte[]> realValues = new LinkedList<>();
            String[] bas = values.replaceAll("^\\[|\\]$", "").split(",(?![^(\\[]*[\\])])");
            for (String ba : bas) {
                realValues.add(JSONToByteArray(ba));
            }
            multi.putAll(key, realValues);
        }
        return multi;
    }

    public static void zipFiles(List<File> files, String dest) throws IOException {
        FileOutputStream fout = new FileOutputStream(dest);
        ZipOutputStream zout = new ZipOutputStream(fout);

        for (File file : files) {
            zout.putNextEntry(new ZipEntry(file.getName()));
            byte[] bytes = Files.readAllBytes(file.toPath());
            zout.write(bytes, 0, bytes.length);
            zout.closeEntry();
        }
        zout.close();
    }

    /**
     * Takes in a list of files and a map of tags and a destination and zips the files and writes a zip in the destination.
     * @param files
     * @param dest
     */
    public static void zipFilesAndTags(List<File> files, Map<String, byte[]> encTags, String dest) throws IOException {
        FileOutputStream fout = new FileOutputStream(dest);
        ZipOutputStream zout = new ZipOutputStream(fout);

        for (File file : files) {
            zout.putNextEntry(new ZipEntry(file.getName()));
            byte[] bytes = Files.readAllBytes(file.toPath());
            zout.write(bytes, 0, bytes.length);
            zout.closeEntry();
        }

        for (String ident : encTags.keySet()) {
            zout.putNextEntry(new ZipEntry(ident + ".tag"));
            byte[] bytes = encTags.get(ident);
            zout.write(bytes, 0, bytes.length);
            zout.closeEntry();
        }

        zout.close();
    }

    public static String stateToJSON(Map<String, Integer> state) {
        return GSON.toJson(state);
    }

    public static Map<String, Integer> JSONToState(String state) {
        Type type = new TypeToken<Map<String, Integer>>(){}.getType();
        return GSON.fromJson(state, type);
    }

    public static String csRandomAlphaNumericString(int numChars) {
        SecureRandom srand = new SecureRandom();
        Random rand = new Random();
        char[] buff = new char[numChars];

        for (int i = 0; i < numChars; ++i) {
            // reseed rand once you've used up all available entropy bits
            if ((i % 10) == 0) {
                rand.setSeed(srand.nextLong()); // 64 bits of random!
            }
            buff[i] = VALID_CHARACTERS[rand.nextInt(VALID_CHARACTERS.length)];
        }
        return new String(buff);
    }

    public static File getDictionaryUpdatesFile(String userID) {
        return new File(Const.USERS_DIR + userID + Const.SSE_DIR + Const.DICT_UPDATE_FILE);
    }

    public static File getDictionaryFile(String userID) {
        return new File(Const.USERS_DIR + userID + Const.SSE_DIR + Const.DICT_FILE);
    }

    public static File getSaltFile(String userID) {
        return new File(Const.USERS_DIR + userID + Const.SSE_DIR + Const.SALT_FILE);
    }

    public static File getStateFile(String userID) {
        return new File(Const.USERS_DIR + userID + Const.SSE_DIR + Const.STATE_FILE);
    }

    public static File getSSEDir(String userID) {
        return new File(Const.USERS_DIR + userID + Const.SSE_DIR);
    }

    public static File getZipDir(String userID) {
        return new File(Const.USERS_DIR + userID + Const.ZIP_DIR);
    }

    public static File getThumbnailDir(String userID) {
        return new File(Const.USERS_DIR + userID + Const.THUMBNAIL_DIR);
    }

    public static File getFullImageDir(String userID) {
        return new File(Const.USERS_DIR + userID + Const.FULL_IMAGE_DIR);
    }

    public static File getMediumImageDir(String userID) {
        return new File(Const.USERS_DIR + userID + Const.MEDIUM_IMAGE_DIR);
    }

    /**
     * Returns the password hashed with the salt.
     * @param password the plaintext password.
     * @return a salted hash.
     */
    public static String hashPassword(String password, String salt) {
        if (password == null) return null;
        return bytesToHex(CryptoPrimitives.scrypt(password.getBytes(), salt.getBytes(), Const.SCRYPT_CPU, Const.SCRYPT_MEM, Const.SCRYPT_PAR, Const.SCRYPT_LENGTH));
    }

    public static String bytesToHex(byte[] input) {
        final StringBuilder builder = new StringBuilder();
        for(byte b : input) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    public static String setupNewDeviceJSON(byte[] salt, byte[] encState, byte[] clientSalt) {
        JsonObjectBuilder jsonBuilder = Json.createObjectBuilder()
                .add(Const.SALT_FILE, Utils.byteArrayToJSON(salt))
                .add(Const.LOCAL_PASSWORD_SALT_LABEL, Utils.byteArrayToJSON(clientSalt))
                .add(Const.STATE_LABEL, Utils.byteArrayToJSON(encState));
        return jsonBuilder.build().toString();
    }

}
