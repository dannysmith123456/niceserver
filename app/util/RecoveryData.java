package util;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import java.util.ArrayList;
import java.util.List;

/**
 * A class that holds and serializes the recovery information in order for it to be sent across the network.
 */
public class RecoveryData {
    private byte[] enc;
    private byte[] salt;
    private byte[] clientSalt;
    private List<Tuple<String, Integer>> securityQuestions = new ArrayList<>();

    public void setEnc(byte[] enc1) {
        this.enc = enc1;
    }

    public void setSalt(byte[] salt1) {
        this.salt = salt1;
    }

    public void setClientSalt(byte[] salt2) {
        this.clientSalt = salt2;
    }

    public void addSecurityQuestion(Tuple<String, Integer> question) {
        this.securityQuestions.add(question);
    }

    /**
     * This serializes the data structure into JSON so we can send it over the network.
     * TODO: fix this mess
     * @return
     */
    public String toJSON() {
        JsonArrayBuilder questionArray = Json.createArrayBuilder();
        for (Tuple<String, Integer> question : this.securityQuestions) {
            questionArray.add(Json.createObjectBuilder()
            .add("question", question.k)
            .add("number", question.v));
        }
        JsonObjectBuilder jsonBuilder = Json.createObjectBuilder()
                .add("enc", Utils.byteArrayToJSON(this.enc))
                .add("salt", Utils.byteArrayToJSON(this.salt))
                .add("clientSalt", Utils.byteArrayToJSON(this.clientSalt))
                .add("questions", questionArray);
        JsonObject json = jsonBuilder.build();
        return json.toString();
    }
}
