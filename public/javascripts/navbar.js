$( document ).ready(function() {
    var myNav = document.getElementById('mynav');
	var logo = document.getElementById('logo');
	window.onscroll = function () { 
	    "use strict";
	    if (document.body.scrollTop >= 50 ) {
	        myNav.classList.add("nav-colored");
	        myNav.classList.remove("nav-transparent");
	    } 
	    else {
	        myNav.classList.add("nav-transparent");
	        myNav.classList.remove("nav-colored");
	    }
	};

	$("#down-arrow").click(function() {
		$.scrollTo(document.getElementById('technology'), 800);
	});

	$("#technology-button").click(function() {
		$.scrollTo(document.getElementById('technology'), 800);
	});

	$("#features-button").click(function() {
		$.scrollTo(document.getElementById('features'), 800);
	});

	$("#team-button").click(function() {
		$.scrollTo(document.getElementById('team'), 800);
	});

	$("#logo").click(function() {
		$.scrollTo(document.getElementById('top'), 800);
	});

	$("#email-input-submit").click(function() {
        var data = $("#email-input-top").val();
        $.post("https://pixek.io/home/signupbeta", {email: data})
        .done(function(data) {
            $("#email-input-submit").css("backgroundColor", "#1ac600");
            $("#email-input-submit").html("Request Sent!");
        });
    });

    $("#email-input-submit-bottom").click(function() {
        var data = $("#email-input-bottom").val();
        $.post("https://pixek.io/home/signupbeta", {email: data})
        .done(function(data) {
            $("#email-input-submit-bottom").css("backgroundColor", "#1ac600");
            $("#email-input-submit-bottom").html("Request Sent!");
        });
    });
});