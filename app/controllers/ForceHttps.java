package controllers;

import play.libs.F;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;

import java.util.concurrent.CompletionStage;

public class ForceHttps extends Action.Simple {

    @Override
    public CompletionStage<Result> call(Http.Context ctx) {
        if (!ctx.request().secure()) {
            return F.Promise.promise(() -> redirect("https://" + ctx.request().host().replace("www.","")
                    + ctx.request().uri()));
        }

        return delegate.call(ctx);
    }
}
