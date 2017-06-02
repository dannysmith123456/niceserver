package controllers;

import com.google.inject.Inject;
import db.UserDatabase;
import org.apache.commons.io.FileUtils;
import play.data.DynamicForm;
import play.data.FormFactory;
import play.db.Database;
import play.mvc.Controller;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;
import play.mvc.With;
import sse.DynRH2LevServerWrapper;
import util.Const;
import util.ImageData;
import util.Tuple;
import util.Utils;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * This class handle the logic with the sse stuff.
 */
@With(ForceHttps.class)
public class EncryptedSearchController extends Controller {

    @Inject
    FormFactory formFactory;

    // we get an instance of our user database
    @Inject
    Database db;

    // depreciated
    /*public Result update() {
        String publicID = session(Const.USER_ID_SESSION_LABEL);
        if (publicID == null) {
            return badRequest("Unauthorized user");
        }
        UserDatabase udb = new UserDatabase(db);
        String id = udb.getUserIDFromPublicID(publicID);
        if (id == null) {
            // if the id is null tell the user they mad a bad request
            return badRequest("Unauthorized user");
        }
        udb.close(); // free this resource

        MultipartFormData<File> body = request().body().asMultipartFormData();
        FilePart<File> picture = body.getFile(Const.PICTURE_LABEL);
        if (picture == null) {
            return badRequest("File missing");
        }
        File pic = picture.getFile();

        DynamicForm requestData = formFactory.form().bindFromRequest();
        String updateToken = requestData.get(Const.UPDATE_TOKEN_LABEL);
        String fileName = requestData.get(Const.PICTURE_NAME_LABEL);

        if (updateToken == null) {
            return badRequest("Update token missing");
        }
        if (fileName == null) {
            return badRequest("File name missing");
        }

        try {
            // We should pass in the image and user id to the wrapper
            DynRH2LevServerWrapper.update(id, pic, fileName, updateToken);
        } catch (Exception e) {
            e.printStackTrace();
            return internalServerError("oops");
        }

        return ok("update successful");
    }*/

    /**
     * Upload only the update token to allow for split requests from the client
     * @return
     */
    /*public Result updateMultiToken() {
        String id = verifyUser();
        if (id == null) {
            return badRequest("Unauthorized user");
        }

        DynamicForm requestData = formFactory.form().bindFromRequest();
        String updateToken = requestData.get(Const.UPDATE_TOKEN_LABEL);
        if (updateToken == null) {
            return badRequest("Missing update token");
        }
        try {
            DynRH2LevServerWrapper.updateMultiToken(id, updateToken);
        } catch (Exception e) {
            e.printStackTrace();
            return internalServerError("Something went wrong");
        }
        return ok("update successful");
    }

    public Result updateMultiFile() {
        String id = verifyUser();
        if (id == null) {
            return badRequest("Unauthorized user");
        }

        MultipartFormData<File> body = request().body().asMultipartFormData();
        DynamicForm requestData = formFactory.form().bindFromRequest();

        List<ImageData> images = new ArrayList<>();
        int picNum = 1;
        FilePart<File> picture = body.getFile(Const.PICTURE_LABEL + picNum);
        FilePart<File> thumb = body.getFile(Const.THUMBNAIL_LABEL + picNum);
        String fileName = requestData.get(Const.PICTURE_NAME_LABEL + picNum);
        if (picture == null || thumb == null || fileName == null) {
            return badRequest("Include at least one picture");
        }
        while (picture != null && thumb != null && fileName != null) {
            images.add(new ImageData(picture.getFile(), thumb.getFile(), fileName));
            picNum++;
            picture = body.getFile(Const.PICTURE_LABEL + picNum);
            thumb = body.getFile(Const.THUMBNAIL_LABEL + picNum);
            fileName = requestData.get(Const.PICTURE_NAME_LABEL + picNum);
        }

        try {
            // We should pass in the image and user id to the wrapper
            DynRH2LevServerWrapper.updateMultiFiles(id, images, db);
        } catch (Exception e) {
            e.printStackTrace();
            return internalServerError("oops");
        }
        return ok("update successful");
    }*/

    /**
     * This handles updating multiple files. Each picture should be labeled as picture1, picture2, etc.
     * @return
     */
    public Result updateMulti() {
        System.out.println("update hit");
        String id = verifyUser();
        if (id == null) {
            return badRequest("Unauthorized user");
        }

        MultipartFormData<File> body = request().body().asMultipartFormData();
        DynamicForm requestData = formFactory.form().bindFromRequest();

        List<ImageData> images = new ArrayList<>();
        int picNum = 1;
        FilePart<File> picture = body.getFile(Const.PICTURE_LABEL + picNum);
        FilePart<File> thumb = body.getFile(Const.THUMBNAIL_LABEL + picNum);
        FilePart<File> medium = body.getFile(Const.MEDIUM_LABEL + picNum);
        String fileName = requestData.get(Const.PICTURE_NAME_LABEL + picNum);
        if (picture == null || thumb == null || fileName == null || medium == null) {
            return badRequest("Include at least one picture");
        }
        while (picture != null && thumb != null && fileName != null && medium != null) {
            images.add(new ImageData(picture.getFile(), thumb.getFile(), medium.getFile(), fileName));
            picNum++;
            picture = body.getFile(Const.PICTURE_LABEL + picNum);
            thumb = body.getFile(Const.THUMBNAIL_LABEL + picNum);
            medium = body.getFile(Const.MEDIUM_LABEL + picNum);
            fileName = requestData.get(Const.PICTURE_NAME_LABEL + picNum);
        }

        String updateToken = requestData.get(Const.UPDATE_TOKEN_LABEL);
        if (updateToken == null) {
            return badRequest("Missing update token");
        }

        try {
            // We should pass in the image and user id to the wrapper
            DynRH2LevServerWrapper.updateMulti(id, images, updateToken, db);
        } catch (Exception e) {
            e.printStackTrace();
            return internalServerError("oops");
        }

        return ok("update successful");
    }

    /**
     * The user sends the tags associated to each picture through this endpoint. The tags are encrypted.
     * @return
     */
    public Result uploadTags() {
        String id = verifyUser();
        if (id == null) {
            return badRequest("Unauthorized user");
        }

        MultipartFormData<File> body = request().body().asMultipartFormData();
        DynamicForm requestData = formFactory.form().bindFromRequest();
        int count = 1;

        FilePart<File> encTagFilePart = body.getFile(Const.TAG_LABEL + count);
        String imageName = requestData.get(Const.PICTURE_NAME_LABEL + count);
        List<Tuple<String, byte[]>> tagPairs = new ArrayList<>();

        while (encTagFilePart != null && imageName != null) {
            try {
                tagPairs.add(new Tuple<>(imageName, FileUtils.readFileToByteArray(encTagFilePart.getFile())));
            } catch (IOException e) {
                e.printStackTrace();
            }
            count++;
            encTagFilePart = body.getFile(Const.TAG_LABEL + count);
            imageName = requestData.get(Const.PICTURE_NAME_LABEL + count);
        }

        // add these image tags to the database
        UserDatabase udb = new UserDatabase(db);
        if (!udb.addImageTags(id, tagPairs)) {
            udb.close();
            return internalServerError("Something went wrong");
        }
        udb.close();
        return ok("Successfully added tags");
    }

    public Result query() {
        System.out.println("Query hit");
        String id = verifyUser();
        if (id == null) {
            return badRequest("Unauthorized user");
        }

        DynamicForm requestData = formFactory.form().bindFromRequest();
        String cmac = requestData.get(Const.CMAC_TOKEN_LABEL);
        String token = requestData.get(Const.QUERY_TOKEN_LABEL);
        String cachedImagesString = requestData.get(Const.CACHED_IMAGE_LABEL);

        if (cmac == null || token == null || cachedImagesString == null) {
            return badRequest("Missing parameters");
        }

        Set<String> cachedImages = new HashSet<>(Arrays.asList(cachedImagesString.split(",")));

        try {
            Tuple<List<File>, Map<String, byte[]>> pair = DynRH2LevServerWrapper.query(id, cmac, token, db, cachedImages);
            List<File> files = pair.k;
            // if the search didn't result in any files, don't send a file
            if (files.size() == 0) {
                return ok("no files");
            }
            // clear the zip directory to save space
            File zipDir = new File(Const.USERS_DIR + id + Const.ZIP_DIR);
            FileUtils.cleanDirectory(zipDir);

            // zip up these files and send it over the server, will need some way to clean up zipped files
            String zipFile = Const.USERS_DIR + id + Const.ZIP_DIR + UUID.randomUUID().toString().replaceAll("-", "");
            Utils.zipFilesAndTags(files, pair.v, zipFile);
            return ok(new File(zipFile));
        } catch (Exception e) {
            e.printStackTrace();
            return internalServerError("Something went wrong");
        }
    }

    /**
     * This is not actually querying the timestamp, instead it uses the image id to get the images in the
     * order they were inserted in the server.
     * @return
     */
    public Result queryTimestamp() {
        String id = verifyUser();
        if (id == null) {
            return badRequest("Unauthorized user");
        }

        DynamicForm requestData = formFactory.form().bindFromRequest();
        String numRequestStr = requestData.get(Const.NUM_REQUEST_LABEL);
        String localImages = requestData.get(Const.LOCAL_IMAGE_NAMES_LABEL);
        if (numRequestStr == null || localImages == null) {
            return badRequest("missing parameters");
        }
        long numRequest = Long.parseLong(numRequestStr);
        Tuple<List<File>, Map<String, byte[]>> pair = DynRH2LevServerWrapper.queryByTimestamp(id, localImages.split(" "), numRequest, db);
        if (pair.k.size() == 0) {
            return badRequest("no files");
        }
        try {
            String zipFile = Const.USERS_DIR + id + Const.ZIP_DIR + UUID.randomUUID().toString().replaceAll("-", "");
            Utils.zipFilesAndTags(pair.k, pair.v, zipFile);
            return ok(new File(zipFile));
        } catch (IOException e) {
            e.printStackTrace();
            return internalServerError("something went wrong");
        }
    }

    public Result resetImageIDCookie() {
        String id = verifyUser();
        if (id == null) {
            return badRequest("Unauthorized user");
        }

        session().remove(Const.LAST_RETRIEVED_IMAGE);
        return ok("cleared cookie");
    }

    public Result getFullImage() {
        String id = verifyUser();
        if (id == null) {
            return badRequest("Unauthorized user");
        }

        DynamicForm requestData = formFactory.form().bindFromRequest();
        String imageName = requestData.get(Const.PICTURE_NAME_LABEL);
        if (imageName == null) {
            return badRequest("Missing image name");
        }
        File image = new File(Const.USERS_DIR + id + Const.FULL_IMAGE_DIR + imageName);
        if (!image.exists()) {
            // if the image doesn't exist, return an error
            return badRequest("Image does not exist");
        }
        List<File> files = new ArrayList<>();
        files.add(image);
        String zipFile = Const.USERS_DIR + id + Const.ZIP_DIR + UUID.randomUUID().toString().replaceAll("-", "");
        try {
            Utils.zipFiles(files, zipFile);
            return ok(new File(zipFile));
        } catch (IOException e) {
            e.printStackTrace();
            return internalServerError("Something went wrong");
        }
    }

    public Result getMediumImage() {
        String id = verifyUser();
        if (id == null) {
            return badRequest("Unauthorized user");
        }

        DynamicForm requestData = formFactory.form().bindFromRequest();
        String imageName = requestData.get(Const.PICTURE_NAME_LABEL);
        if (imageName == null) {
            return badRequest("Missing image name");
        }

        File image = new File(Const.USERS_DIR + id + Const.MEDIUM_IMAGE_DIR + imageName);
        if (!image.exists()) {
            // if the image doesn't exist, return an error
            return badRequest("Image does not exist");
        }
        List<File> files = new ArrayList<>();
        files.add(image);
        String zipFile = Const.USERS_DIR + id + Const.ZIP_DIR + UUID.randomUUID().toString().replaceAll("-", "");
        try {
            Utils.zipFiles(files, zipFile);
            return ok(new File(zipFile));
        } catch (IOException e) {
            e.printStackTrace();
            return internalServerError("Something went wrong");
        }
    }

    /**
     * The user sends the name of the image they wish to delete. We delete the image from the file system
     * as well as remove all traces of it from the database.
     * @return
     */
    public Result deleteImage() {
        String id = verifyUser();
        if (id == null) {
            return badRequest("Unauthorized user");
        }

        DynamicForm requestData = formFactory.form().bindFromRequest();
        String imageName = requestData.get(Const.PICTURE_NAME_LABEL);
        if (imageName == null) {
            return badRequest("Missing image name");
        }

        DynRH2LevServerWrapper.deleteImage(id, imageName, db);
        return ok("deleted image");
    }

    /**
     * THe user sends an encrypted version of their state to the cloud so that we can back it up in
     * case of the user losing their phone or their app getting deleted or if they get a new phone.
     * The state being sent should be sent in the form of a file, as it will be a collection of bytes.
     * If the user loses their state, they will only be able to query the images they have uploaded before
     * they backed up their state.
     * @return ok if they successfully backed up their state, failure otherwise
     */
    public Result backupState() {
        String id = verifyUser();
        if (id == null) {
            return badRequest("Unauthorized user");
        }

        MultipartFormData<File> body = request().body().asMultipartFormData();
        FilePart<File> stateFilePart = body.getFile(Const.STATE_LABEL);
        File stateFile = stateFilePart.getFile();
        try {
            DynRH2LevServerWrapper.backupState(id, stateFile);
        } catch (IOException e) {
            e.printStackTrace();
            return internalServerError("Could not write state");
        }
        return ok("State saved");
    }

    public Result getImageTags() {
        String id = verifyUser();
        if (id == null) {
            return badRequest("Unauthorized user");
        }

        DynamicForm requestData = formFactory.form().bindFromRequest();
        String imageName = requestData.get(Const.PICTURE_NAME_LABEL);
        if (imageName == null) {
            return badRequest("Missing image name");
        }

        UserDatabase udb = new UserDatabase(db);
        byte[] encTags = udb.getImageTags(id, imageName);
        udb.close();
        if (encTags == null) {
            return badRequest("Could not find tags for imagse with that name");
        }
        return ok(encTags);
    }

    private String verifyUser() {
        String publicID = session(Const.USER_ID_SESSION_LABEL);
        if (publicID == null) {
            return null;
        }
        UserDatabase udb = new UserDatabase(db);
        String id = udb.getUserIDFromPublicID(publicID);
        udb.close();
        if (id == null) {
            // if the id is null tell the user they mad a bad request
            return null;
        }
        return id;
    }

}
