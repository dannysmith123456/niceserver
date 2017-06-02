package db;

import com.google.inject.Inject;
import org.h2.command.Prepared;
import util.Const;
import util.RecoveryData;
import util.Tuple;
import util.Utils;
import org.crypto.sse.CryptoPrimitives;
import play.db.Database;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class UserDatabase {

    private Database db;
    private Connection conn;

    @Inject
    public UserDatabase(Database db) {
        this.db = db;
        this.conn = db.getConnection();
    }

    /**
     * adds a user to this database if it doesn't already exist.
     * @param firstName the first name of the user.
     * @param lastName the last name of the user.
     * @param email the email of the user.
     * @param password the hashed password of the user.
     * @return true if we successfully added user to the database.
     */
    public boolean addUserIfNotExists(String firstName, String lastName, String email, String password, String salt, byte[] clientSalt) {
        // first check if the user email already exists
        try {
            String query = "SELECT * FROM users WHERE users.email = ?";
            PreparedStatement prep = conn.prepareStatement(query);
            prep.setString(1, email);
            ResultSet rs = prep.executeQuery();
            if (rs.next()) {
                // if our result set is not empty, it means this user already exists
                return false;
            }
            // close our resources
            rs.close();
            prep.close();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }

        // if we have reached this point, it means we can insert this user
        try {
            String insert = "INSERT INTO users (public_id, first_name, last_name, email, password, salt, client_salt) VALUES(?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement insertPrep = conn.prepareStatement(insert);
            // insert a long random string as the users public id
            insertPrep.setString(1, generateUniquePublicID());
            insertPrep.setString(2, firstName);
            insertPrep.setString(3, lastName);
            insertPrep.setString(4, email);
            insertPrep.setString(5, password);
            insertPrep.setString(6, salt);
            insertPrep.setBytes(7, clientSalt);

            if (insertPrep.executeUpdate() == 0) {
                insertPrep.close();
                return false;
            }
            insertPrep.close();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }

        // if we got here it means everything was successful
        return true;
    }

    /**
     * Generates a unique public ID within 1000 tries. Statistically we should always require only 1 try.
     * @return a long random string that is the  public id.
     * @throws SQLException
     */
    private String generateUniquePublicID() throws SQLException {
        int counter = 0;
        while (counter < 1000) {
            String candidateID = Utils.csRandomAlphaNumericString(100);
            String query = "SELECT id FROM users WHERE public_id = ?";
            PreparedStatement prep = conn.prepareStatement(query);
            prep.setString(1, candidateID);
            ResultSet rs = prep.executeQuery();
            if (!rs.next()) {
                rs.close();
                prep.close();
                return candidateID;
            }
            rs.close();
            prep.close();
            counter++;
        }
        throw new SQLException("Could not generate a unique ID after 1000 tries");
    }

    /**
     * Given a public id, verifies that the public id exists and returns the user id of the user with
     * that public id.
     * @param publicID
     * @return the user id if the public id is correct, null otherwise.
     */
    public String getUserIDFromPublicID(String publicID) {
        String query = "SELECT id FROM users WHERE public_id = ?";
        try {
            PreparedStatement prep = conn.prepareStatement(query);
            prep.setString(1, publicID);
            ResultSet rs = prep.executeQuery();
            if (!rs.next()) {
                return null;
            }
            String id = rs.getString("id");
            prep.close();
            rs.close();
            return id;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Gets the client salt for the user.
     * @param userID
     * @return
     */
    public byte[] getClientSaltFromUserID(String userID) {
        try {
            String query = "SELECT client_salt FROM users WHERE id = ?";
            PreparedStatement prep = conn.prepareStatement(query);
            prep.setString(1, userID);
            ResultSet rs = prep.executeQuery();
            byte[] clientSalt = null;
            if (rs.next()) {
                clientSalt = rs.getBytes("client_salt");
            }
            rs.close();
            prep.close();
            return clientSalt;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Adds the security questions to the database and encrypts the user's password based on their answers.
     * @param securityQuestion
     * @param securityQuestion2
     * @return true if successfully completed.
     */
    public boolean addUserRecovery(int userID, String securityQuestion, String securityQuestion2,
                                   String encPassword, String passwordSalt) {
        try {
            String insert = "INSERT INTO sec_qs (u_id, q_text, q_num) VALUES (?, ?, ?)";
            PreparedStatement insertPrep = conn.prepareStatement(insert);
            insertPrep.setInt(1, userID);
            insertPrep.setString(2, securityQuestion);
            insertPrep.setInt(3, 1);
            insertPrep.addBatch();
            // insert second question
            insertPrep.setInt(1, userID);
            insertPrep.setString(2, securityQuestion2);
            insertPrep.setInt(3, 2);
            insertPrep.addBatch();

            insertPrep.executeBatch();
            insertPrep.close();

            insert = "INSERT INTO recovery (u_id, enc, salt) VALUES (?, ?, ?)";
            insertPrep = conn.prepareStatement(insert);
            insertPrep.setInt(1, userID);
            insertPrep.setBytes(2, Utils.JSONToByteArray(encPassword));
            insertPrep.setBytes(3, Utils.JSONToByteArray(passwordSalt));

            insertPrep.executeUpdate();
            insertPrep.close();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Generates a secure random string for the link that the user needs to click on
     * @param userID
     * @return
     */
    public Tuple<String, String> addUserVerification(int userID) {
        try {
            String randomString = Utils.csRandomAlphaNumericString(10);
            String tempID = Utils.csRandomAlphaNumericString(10);
            String insert = "INSERT INTO verification (u_id, code, temp_u_id) VALUES (?, ?, ?)";
            PreparedStatement prep = conn.prepareStatement(insert);
            prep.setInt(1, userID);
            prep.setString(2, randomString);
            prep.setString(3, tempID);
            prep.executeUpdate();
            prep.close();
            return new Tuple<>(tempID, randomString);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Generates a single code for the user id and leaves the temp ID as blank
     * @param userID
     * @return
     */
    public String addUserVerificationSingleCode(int userID) {
        try {
            String randomString = Utils.csRandomAlphaNumericString(10);
            String tempID = "";
            String insert = "INSERT INTO verification (u_id, code, temp_u_id) VALUES (?, ?, ?)";
            PreparedStatement prep = conn.prepareStatement(insert);
            prep.setInt(1, userID);
            prep.setString(2, randomString);
            prep.setString(3, tempID);
            prep.executeUpdate();
            prep.close();
            return randomString;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean verifyUser(String tempID, String code) {
        try {
            // first check whether or not the user is correctly verified
            String query = "SELECT u_id FROM verification WHERE temp_u_id = ? AND code = ? AND used = 0";
            PreparedStatement prep = conn.prepareStatement(query);
            prep.setString(1, tempID);
            prep.setString(2, code);
            ResultSet rs = prep.executeQuery();
            if (!rs.next()) {
                return false;
            }
            int userID = rs.getInt("u_id");
            prep.close();
            rs.close();

            // update the temp id and code to be used
            String updateUsed = "UPDATE verification SET used = 1 WHERE temp_u_id = ? AND code = ?";
            prep = conn.prepareStatement(updateUsed);
            prep.setString(1, tempID);
            prep.setString(2, code);
            prep.executeUpdate();
            prep.close();

            // now set the user to verified in the user table
            String update = "UPDATE users SET verified = 1 WHERE id = ?";
            prep = conn.prepareStatement(update);
            prep.setInt(1, userID);
            prep.executeUpdate();
            prep.close();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Checks whether or not the user credentials are correct.
     * @param email the email of the user.
     * @param password the hashed password of the user.
     * @return true if the credentials match, false otherwise.
     */
    public boolean validateUser(String email, String password) {
        try {
            // first get the salt
            String saltQuery = "SELECT salt FROM users WHERE users.email = ?";
            PreparedStatement saltPrep = conn.prepareStatement(saltQuery);
            saltPrep.setString(1, email);
            ResultSet saltRs = saltPrep.executeQuery();
            String salt = "";
            // if the salt exists, get it
            if (saltRs.next()) {
                salt = saltRs.getString("salt");
            } else {
                return false; // otherwise the email is incorrect
            }
            // make sure user has validated their account
            String query = "SELECT * FROM users WHERE users.email = ? AND users.password = ? AND verified = 1";
            PreparedStatement prep = conn.prepareStatement(query);
            prep.setString(1, email);
            prep.setString(2, Utils.hashPassword(password, salt)); // make sure to use the salt
            ResultSet rs = prep.executeQuery();
            if (!rs.next()) {
                return false;
            }
            rs.close();
            prep.close();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Fetches the id of the user with the given email.
     * @param email the email of the user.
     * @return the user id if it exist, -1 otherwise.
     */
    public int getUserID(String email) {
        try {
            String query = "SELECT id FROM users WHERE users.email = ?";
            PreparedStatement prep = conn.prepareStatement(query);
            prep.setString(1, email);
            ResultSet rs = prep.executeQuery();
            if (!rs.next()) {
                return -1;
            }
            int id =  rs.getInt("id");
            prep.close();
            rs.close();
            return id;
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Fetches the public id of the user with the given email.
     * @param email the email of the user.
     * @return the user if it exists, null otherwise.
     */
    public String getUserPublicID(String email) {
        try {
            String query = "SELECT public_id FROM users WHERE users.email = ?";
            PreparedStatement prep = conn.prepareStatement(query);
            prep.setString(1, email);
            ResultSet rs = prep.executeQuery();
            if (!rs.next()) {
                rs.close();
                prep.close();
                return null;
            }
            String publicID = rs.getString("public_id");
            prep.close();
            rs.close();
            return publicID;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Updates the database with the timestamp of when this image was uploaded to the server.
     * This is okay to store in the clear because the server knows this anyways.
     * @param userID the id of the user.
     * @param imageName the name of the image.
     * @return true if we have updated the database succesfully.
     */
    public boolean insertImageTimestamp(int userID, String imageName) {
        try {
            String insert = "INSERT INTO image_times (u_id, name) VALUES (?, ?)";
            PreparedStatement prep = conn.prepareStatement(insert);
            prep.setInt(1, userID);
            prep.setString(2, imageName);
            if (prep.executeUpdate() == 0) {
                prep.close();
                return false;
            }
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public long getImageTimestamp(int userID, String imageName) {
        // if we've never queried for an image, then just use our current timestamp
        if (imageName == null) {
            return System.currentTimeMillis() / 1000;
        }
        try {
            String query = "SELECT time FROM image_times WHERE u_id = ? AND name = ?";
            PreparedStatement prep = conn.prepareStatement(query);
            prep.setInt(1, userID);
            prep.setString(2, imageName);
            ResultSet rs = prep.executeQuery();
            if (!rs.next()) {
                return -1;
            }
            long timestamp = rs.getLong("time");
            prep.close();
            rs.close();
            return timestamp;
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    public int getImageID(int userID, String imageName) {
        if (imageName == null) {
            return Integer.MAX_VALUE;
        }
        try {
            String query = "SELECT im_id FROM image_times WHERE u_id = ? AND name = ?";
            PreparedStatement prep = conn.prepareStatement(query);
            prep.setInt(1, userID);
            prep.setString(2, imageName);
            ResultSet rs = prep.executeQuery();
            if (!rs.next()) {
                prep.close();
                rs.close();
                return -1;
            }
            int imID = rs.getInt("im_id");
            prep.close();
            rs.close();
            return imID;
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    /*public List<String> getImagesLessThanID(int userID, int imageID, long numQuery) {
        try {
            // this query insures the last one retrieved is the earliest
            String query = "SELECT name FROM image_times WHERE u_id = ? AND im_id < ? ORDER BY im_id DESC LIMIT ?";
            PreparedStatement prep = conn.prepareStatement(query);
            prep.setInt(1, userID);
            prep.setInt(2, imageID);
            prep.setLong(3, numQuery);
            ResultSet rs = prep.executeQuery();
            List<String> idents = new ArrayList<>();
            while (rs.next()) {
                String imageName = rs.getString("name");
                idents.add(imageName);
            }
            // the last file in the list will be the earliest one
            return idents;
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }*/

    public List<String> getImagesNotInSet(String userID, Set<String> localImages, long numQuery) {
        try {
            String query = "SELECT name FROM image_times WHERE u_id = ? ORDER BY im_id DESC";
            PreparedStatement prep = conn.prepareStatement(query);
            prep.setString(1, userID);
            ResultSet rs = prep.executeQuery();
            List<String> nameList = new ArrayList<>();
            while (rs.next()) {
                String name = rs.getString("name");
                if (!localImages.contains(name)) {
                    nameList.add(name);
                }
                if (nameList.size() >= numQuery) {
                    break;
                }
            }
            rs.close();
            prep.close();
            return nameList;
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public boolean verifyPasswordRecovery(String tempID, String code) {
        try {
            // get time five minutes ago
            long fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000);
            // first check whether or not the user is correctly verified
            String query = "SELECT u_id FROM verification WHERE temp_u_id = ? AND code = ? AND time > ? AND used = 0";
            PreparedStatement prep = conn.prepareStatement(query);
            prep.setString(1, tempID);
            prep.setString(2, code);
            prep.setTimestamp(3, new Timestamp(fiveMinutesAgo));
            ResultSet rs = prep.executeQuery();
            if (!rs.next()) {
                return false;
            }
            prep.close();
            rs.close();

            // update the temp id and code to be used
            String updateUsed = "UPDATE verification SET used = 1 WHERE temp_u_id = ? AND code = ?";
            prep = conn.prepareStatement(updateUsed);
            prep.setString(1, tempID);
            prep.setString(2, code);
            prep.executeUpdate();
            prep.close();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public RecoveryData getPasswordRecoveryInfo(String code) {
        try {
            // we need to find recovery info using the temp id
            String query = "SELECT recovery.enc, recovery.salt, recovery.u_id FROM recovery, verification WHERE "
                    + "verification.code = ? AND recovery.u_id = verification.u_id";
            PreparedStatement prep = conn.prepareStatement(query);
            prep.setString(1, code);
            ResultSet rs = prep.executeQuery();
            if (!rs.next()) {
                return null;
            }
            RecoveryData recoveryData = new RecoveryData();
            byte[] enc = rs.getBytes("enc");
            byte[] salt = rs.getBytes("salt");
            int id = rs.getInt("u_id");
            // free these resources
            prep.close();
            rs.close();
            recoveryData.setEnc(enc);
            recoveryData.setSalt(salt);
            // now we need to get the security questions
            query = "SELECT q_text, q_num FROM sec_qs WHERE u_id = ?";
            prep = conn.prepareStatement(query);
            prep.setInt(1, id);
            rs = prep.executeQuery();
            // add security questions
            while (rs.next()) {
                Tuple<String, Integer> question = new Tuple<>(rs.getString("q_text"), rs.getInt("q_num"));
                recoveryData.addSecurityQuestion(question);
            }
            // free resources
            prep.close();
            rs.close();
            return recoveryData;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public String getUserIDFromVerificationCode(String code) {
        try {
            String query = "SELECT users.id FROM users, verification WHERE verification.code = ? " +
                    "AND verification.u_id = users.id";
            PreparedStatement prep = conn.prepareStatement(query);
            prep.setString(1, code);
            ResultSet rs = prep.executeQuery();
            if (!rs.next()) {
                prep.close();
                rs.close();
                return null;
            }
            String userID = rs.getString("id");
            prep.close();
            rs.close();
            return userID;
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Adds the encrypted image tags to the database.
     * @param userID
     * @param tagPairs
     * @return
     */
    public boolean addImageTags(String userID, List<Tuple<String, byte[]>> tagPairs) {
        try {
            String insert = "INSERT INTO enc_tags (u_id, name, tag) VALUES (?, ?, ?)";
            PreparedStatement prep = conn.prepareStatement(insert);
            for (Tuple<String, byte[]> pair : tagPairs) {
                prep.setString(1, userID);
                prep.setString(2, pair.k);
                prep.setBytes(3, pair.v);
                prep.addBatch();
            }
            prep.executeBatch();
            prep.close();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Given a user id and the name of an image, return the encrypted tags for that image
     * @param userID the user's true id
     * @param imageName the name of the image
     * @return encrypted tags, or null if image is not found
     */
    public byte[] getImageTags(String userID, String imageName) {
        try {
            String query = "SELECT tag FROM enc_tags WHERE u_id = ? AND name = ?";
            PreparedStatement prep = conn.prepareStatement(query);
            prep.setString(1, userID);
            prep.setString(2, imageName);
            ResultSet rs = prep.executeQuery();
            byte[] encTags = null;
            if (rs.next()) {
                encTags = rs.getBytes("tag");
            }
            rs.close();
            prep.close();
            return encTags;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Deletes all traces of this image from the database
     * @param userID
     * @param imageName
     */
    public boolean deleteImage(String userID, String imageName) {
        try {
            String deleteTags = "DELETE FROM enc_tags WHERE u_id = ? AND name = ?";
            PreparedStatement prep = conn.prepareStatement(deleteTags);
            prep.setString(1, userID);
            prep.setString(2, imageName);
            prep.executeUpdate();
            prep.close();

            String deleteTimes = "DELETE FROM image_times WHERE u_id = ? AND name = ?";
            prep = conn.prepareStatement(deleteTimes);
            prep.setString(1, userID);
            prep.setString(2, imageName);
            prep.executeUpdate();
            prep.close();
            return true;
        } catch (SQLException e ){
            e.printStackTrace();
        }
        return false;
    }

    public boolean signupBeta(String email) {
        try {
            String insert = "INSERT INTO signup_beta (email) VALUES (?)";
            PreparedStatement prep = conn.prepareStatement(insert);
            prep.setString(1, email);
            prep.executeUpdate();
            prep.close();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void close() {
        try {
            conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
