package controllers;

import com.google.inject.Inject;
import db.UserDatabase;
import play.data.DynamicForm;
import play.data.FormFactory;
import play.db.Database;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.With;
import views.html.index;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

/**
 * This controller contains an action to handle HTTP requests
 * to the application's home page.
 */
@With(ForceHttps.class)
public class HomeController extends Controller {

    @Inject
    FormFactory formFactory;

    // we get an instance of our user database
    @Inject
    Database db;

    /**
     * An action that renders an HTML page with a welcome message.
     * The configuration in the <code>routes</code> file means that
     * this method will be called when the application receives a
     * <code>GET</code> request with a path of <code>/</code>.
     */
    public Result index() {
        //return ok(index.render("Pixek"));
        return ok();
    }

    /**
     * A handler for when people register for beta
     * @return
     */
    public Result signupBeta() {
        DynamicForm requestData = formFactory.form().bindFromRequest();
        String email = requestData.get("email");
        if (email == null) {
            return badRequest("Need an email");
        }
        UserDatabase udb = new UserDatabase(db);
        udb.signupBeta(email);
        udb.close();
        return ok("ok");
    }

}
