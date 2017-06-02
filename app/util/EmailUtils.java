package util;

import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.SimpleEmail;

public class EmailUtils {

    /**
     * Sends a verification email to the user.
     * @param firstName the name of the user.
     * @param emailAddr the email of the user.
     * @param pair a tuple containing a random code and random user id.
     * @return true if the email was successfully sent.
     */
    public static boolean sendVerificationEmail(String firstName, String emailAddr, Tuple<String, String> pair) {
        String link = "https://pixek.io/account/verify?code=" + pair.v + "&id=" + pair.k;
        String message = "Hello " + firstName + ",\n\nPlease click the following link to verify your Pixek account.\n" + link;
        String subject = "Verify your account";
        return sendEmail(emailAddr, subject, message);
    }

    public static boolean sendRecoveryEmail(String emailAddr, String code) {
        String message = "Hello,\n\nWe have received a request from you to recover your password."
                + " Your code is " + code + ". If you did not make this request,"
                + " please ignore this message.\n\nThe Pixek Team";
        String subject = "Recover your password";
        return sendEmail(emailAddr, subject, message);
    }

    public static boolean sendRegisterNewDeviceEmail(String emailAddr, String code) {
        String message = "Hello,\n\nWe have received a request from you register a new device or app installation."
                + " Your code is " + code + ". If you did not make this request,"
                + " please ignore this message.\n\nThe Pixek Team";
        String subject = "Recover your password";
        return sendEmail(emailAddr, subject, message);
    }

    private static boolean sendEmail(String emailAddr, String subject, String message) {
        try {
            Email email = new SimpleEmail();
            email.setHostName("smtp.googlemail.com");
            email.setSmtpPort(465);
            email.setAuthenticator(new DefaultAuthenticator("hello@pixek.io", "p1x3kadm1ns"));
            email.setSSLOnConnect(true);
            email.setFrom("hello@pixek.io");
            email.setSubject(subject);
            email.setMsg(message);
            email.addTo(emailAddr);
            email.send();
            return true;
        } catch (EmailException e) {
            e.printStackTrace();
            return false;
        }
    }

}
