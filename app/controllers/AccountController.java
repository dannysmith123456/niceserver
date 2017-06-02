package controllers;

import com.google.common.collect.ArrayListMultimap;
import db.UserDatabase;
import org.apache.commons.io.FileUtils;
import org.crypto.sse.CryptoPrimitives;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import play.data.DynamicForm;
import play.data.FormFactory;
import play.db.Database;
import play.mvc.Controller;
import play.mvc.Result;

import com.google.inject.Inject;
import play.mvc.With;
import util.*;

import javax.json.Json;
import javax.json.JsonObject;
import java.io.*;
import java.util.HashMap;

/**
 * This class contains controllers that handle user creation and authentication.
 */
@With(ForceHttps.class)
public class AccountController extends Controller {

    private final int SALT_SIZE = 8;

    @Inject
    FormFactory formFactory;

    // we get an instance of our user database
    @Inject
    Database db;

    /**
     * Should be called to initiate user sign up. Upon proper sign up, the username and hashed password
     * is saved in the db, and the directory for the user is created.
     * @return an ok if the account was created successfully, otherwise an error.
     */
    public Result signup() {
        DynamicForm requestData = formFactory.form().bindFromRequest();
        String firstName = requestData.get(Const.FIRST_NAME_LABEL);
        String lastName = requestData.get(Const.LAST_NAME_LABEL);
        String email = requestData.get(Const.EMAIL_LABEL);
        String passwordSalt = Utils.csRandomAlphaNumericString(SALT_SIZE);
        String password = Utils.hashPassword(requestData.get(Const.PASSWORD_LABEL), passwordSalt);
        String securityQuestion = requestData.get(Const.SECURITY_QUESTION_LABEL);
        String securityQuestion2 = requestData.get(Const.SECURITY_QUESTION_LABEL + 2);
        String clientPasswordSaltString = requestData.get(Const.LOCAL_PASSWORD_SALT_LABEL);
        String encryptedPassword = requestData.get(Const.ENCRYPTED_PASSWORD_LABEL);
        String encryptedPasswordSalt = requestData.get(Const.ENCRYPTED_PASSWORD_SALT_LABEL);

        // make sure all these fields exist
        // in the future we can do more rigorous checks for valid emails and good passwords
        if (clientPasswordSaltString == null || firstName == null || lastName == null || email == null || password == null) {
            return badRequest("Parameters missing");
        }

        byte[] clientPasswordSalt = Utils.JSONToByteArray(clientPasswordSaltString);

        UserDatabase udb = new UserDatabase(db);
        boolean successfullyAddedUser = udb.addUserIfNotExists(firstName, lastName, email, password, passwordSalt, clientPasswordSalt);
        int userID = udb.getUserID(email);
        successfullyAddedUser = successfullyAddedUser && udb.addUserRecovery(userID, securityQuestion, securityQuestion2, encryptedPassword, encryptedPasswordSalt);
        Tuple<String, String> verificationPair = udb.addUserVerification(userID);
        udb.close(); // free this resource

        // now we have to make a directory for this user, we can just make the directory their id
        File directory = new File(Const.USERS_DIR + userID);

        // if this directory somehow exists, then something went wrong
        if (directory.exists()) {
            return internalServerError("User folder already exists");
        }

        directory.mkdirs();

        // make a hidden folder to store their sse data structure information
        File sseInfo = Utils.getSSEDir(userID + "");
        sseInfo.mkdir();

        // make a hidden folder to store zipped stuff about to be sent
        File zipDir = Utils.getZipDir(userID + "");
        zipDir.mkdir();

        // make a folder to store the thumbnails
        File thumbnailDir = Utils.getThumbnailDir(userID + "");
        thumbnailDir.mkdir();

        // make a folder to store full images
        File imageDir = Utils.getFullImageDir(userID + "");
        imageDir.mkdir();

        // make a folder to store medium images
        File mediumDir = Utils.getMediumImageDir(userID + "");
        mediumDir.mkdir();

        // add empty files to the data structures to the hidden folder
        try {
            createEmptyDataStructures(userID);
        } catch (Exception e) {
            e.printStackTrace();
            return internalServerError("Something messed up internally");
        }

        String salt = "";
        try {
            byte[] saltBytes = (byte[]) new ObjectInputStream(new FileInputStream(Utils.getSaltFile(userID + ""))).readObject();
            salt = Utils.byteArrayToJSON(saltBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return internalServerError("could not read salt");
        }

        if (successfullyAddedUser) {
            // send them a verification email
            EmailUtils.sendVerificationEmail(firstName, email, verificationPair);
            // send them both salts in JSON format
            JsonObject jsonObject = Json.createObjectBuilder()
                    .add(Const.SALT_FILE, salt)
                    .add(Const.LOCAL_PASSWORD_SALT_LABEL, Utils.byteArrayToJSON(clientPasswordSalt)).build();
            return ok(jsonObject.toString());
        }
        return badRequest("Could not register user");
    }

    private void createEmptyDataStructures(int userID) throws IOException {
        //File dictionaryUpdates = Utils.getDictionaryUpdatesFile(userID + "");
        File dictionary = Utils.getDictionaryFile(userID + "");
        File salt = Utils.getSaltFile(userID + "");
        //new ObjectOutputStream(new FileOutputStream(dictionaryUpdates)).writeObject(new HashMap<String, byte[]>());
        new ObjectOutputStream(new FileOutputStream(dictionary)).writeObject(ArrayListMultimap.<String, byte[]>create());
        new ObjectOutputStream(new FileOutputStream(salt)).writeObject(CryptoPrimitives.randomBytes(SALT_SIZE));
    }


    /**
     * Should be called when a user logs into the app. Sends an ok and a session token if the login is
     * successful, otherwise returns an error.
     * @return
     */
    public Result login() {
        DynamicForm requestData = formFactory.form().bindFromRequest();
        String email = requestData.get(Const.EMAIL_LABEL); // should get some sort of uid of the user
        String pass = requestData.get(Const.PASSWORD_LABEL); // should get password of the user
        UserDatabase udb = new UserDatabase(db);
        boolean isValidUser = udb.validateUser(email, pass);
        if (!isValidUser) {
            udb.close();
            return badRequest("Password or email is incorrect or not validated");
        }
        String publicID = udb.getUserPublicID(email);
        udb.close();
        if (publicID == null) {
            return badRequest("Error logging in");
        }
        session(Const.USER_ID_SESSION_LABEL, publicID);
        // return the salt to the user so they can generate their key
        return ok();
    }

    /**
     * Sends a password recovery temporary ID and passcode to the user's email to begin the
     * password recovery process.
     * @return
     */
    public Result requestRecover() {
        DynamicForm requestData = formFactory.form().bindFromRequest();
        String email = requestData.get(Const.EMAIL_LABEL);
        if (email == null) {
            return badRequest("incorrect params");
        }
        UserDatabase udb = new UserDatabase(db);
        int userID = udb.getUserID(email);
        if (userID < 0) {
            udb.close();
            return badRequest("bad argument");
        }
        String code = udb.addUserVerificationSingleCode(userID);
        if (EmailUtils.sendRecoveryEmail(email, code)) {
            udb.close();
            return ok("Sent email");
        }
        udb.close();
        return internalServerError("Something went wrong");
    }

    /**
     * Checks to see whether or not the temporary id and key sent by the user is correct, and if so,
     * sends the user the encrypted password and security questions. This is time based, we will give
     * the user 5 minutes to complete the request.
     * @return
     */
    public Result recoverPassword() {
        DynamicForm requestData = formFactory.form().bindFromRequest();
        String code = requestData.get(Const.CODE_LABEL);
        if (code == null) {
            return badRequest("Incorrect params");
        }
        UserDatabase udb = new UserDatabase(db);
        if (udb.verifyPasswordRecovery("", code)) {
            // give the user the encrypted password and security questions
            RecoveryData recoveryData = udb.getPasswordRecoveryInfo(code);
            if (recoveryData != null) {
                udb.close();
                // return a json representation of the data
                return ok(recoveryData.toJSON());
            }
        }
        udb.close();
        return badRequest("The information you have provided is incorrect, or your code has timed out");
    }

    public Result requestRegister() {
        DynamicForm requestData = formFactory.form().bindFromRequest();
        String email = requestData.get(Const.EMAIL_LABEL);
        if (email == null) {
            return badRequest("incorrect params");
        }
        UserDatabase udb = new UserDatabase(db);
        int userID = udb.getUserID(email);
        if (userID < 0) {
            udb.close();
            return badRequest("bad argument");
        }
        String code = udb.addUserVerificationSingleCode(userID);
        if (EmailUtils.sendRegisterNewDeviceEmail(email, code)) {
            udb.close();
            return ok("Sent email");
        }
        udb.close();
        return internalServerError("Something went wrong");
    }

    public Result registerNewDevice() {
        DynamicForm requestData = formFactory.form().bindFromRequest();
        String code = requestData.get(Const.CODE_LABEL);
        if (code == null) {
            return badRequest("Incorrect params");
        }
        UserDatabase udb = new UserDatabase(db);
        String userID = udb.getUserIDFromVerificationCode(code);
        byte[] clientSalt = udb.getClientSaltFromUserID(userID);
        udb.close();
        if (userID == null) {
            return badRequest("Could not verify user");
        }
        try {
            byte[] saltBytes = (byte[]) new ObjectInputStream(new FileInputStream(Utils.getSaltFile(userID))).readObject();
            byte[] encState = FileUtils.readFileToByteArray(Utils.getStateFile(userID));
            String registerJSON = Utils.setupNewDeviceJSON(saltBytes, encState, clientSalt);
            return ok(registerJSON);
        } catch (Exception e) {
            e.printStackTrace();
            return internalServerError("Something went wrong");
        }
    }

    public Result verify(String code, String id) {
        if (code.equals("")) return badRequest("Missing verification code");
        // we now try to verify the user
        UserDatabase udb = new UserDatabase(db);
        if (udb.verifyUser(id, code)) {
            udb.close();
            return ok("You are all set!");
        }
        udb.close();
        return badRequest("Incorrect information");
    }

}
