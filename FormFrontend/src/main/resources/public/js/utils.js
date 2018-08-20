function validateLogin() {
    var loginButton = $("#login-button");
    if (loginButton.text().startsWith("Log in")) {
        var error = false;
        var username = $("#username");
        var password = $("#password");

        if (username.val().length < 4) {
            error = true;
            username.addClass("uk-form-danger");
            UIkit.notification('The username must be at least 4 characters', 'danger');
        } else username.removeClass("uk-form-danger");

        if (password.val().length < 4) {
            error = true;
            password.addClass("uk-form-danger");
            UIkit.notification('The password must be at least 4 characters', 'danger');
        } else password.removeClass("uk-form-danger");

        if (!error) {
            $.post("/login", {username: username.val(), password: password.val()}, function (data) {
                console.log(data);
                console.log(data.status);
                console.log(data.status === 200);
                if (data.status === 200) {
                    window.location.replace(decodeURIComponent(params.redirect))
                } else {
                    password.val('');
                    UIkit.notification(data.message, 'danger');
                }
            }, "json")
        }
    }
}

function passwordKeypressEnter() {
    $(document).ready(function () {
        $('#password').keypress(function (key) {
            if (key.keyCode === 13) validateLogin()
        });
    });
}


// !!!!!!!!!!! The following functions come from https://stackoverflow.com/a/5448635 !!!!!! This is not my code!

function getSearchParameters() {
    var prmstr = window.location.search.substr(1);
    return prmstr != null && prmstr !== "" ? transformToAssocArray(prmstr) : {};
}

function transformToAssocArray(prmstr) {
    var params = {};
    var prmarr = prmstr.split("&");
    for (var i = 0; i < prmarr.length; i++) {
        var tmparr = prmarr[i].split("=");
        params[tmparr[0]] = tmparr[1];
    }
    return params;
}

var params = getSearchParameters();

