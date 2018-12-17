$(function () {
    $("#change-email").click(function () {
        if (emailSet) remove("remove-email");
        else remove("add-email");
    });

    $("#notification-update").click(function () {
        let submitObject = {
            'type': 'notification-update',
            'newSubmission': $("#notification-new-submission").prop("checked"),
            'deleteSubmission': $("#notification-delete-submission").prop("checked")
        };

        update(submitObject);
    });

    $("#form-specific-notification-update").click(function () {
        let submitObject = {
            'type': 'notification-form-update',
            'formId': formId,
            'newSubmission': $("#notification-new-submission").prop("checked"),
            'deleteSubmission': $("#notification-delete-submission").prop("checked")
        };
        update(submitObject)
    });
});

let queuedActions = [];


function remove(name) {
    if (queuedActions.find(action => action === name) === undefined) {
        let message = undefined;
        if (name === "remove-email") message = "remove your linked email account";
        else if (name === "add-email") message = "link an email account";
        UIkit.notification({
            message: 'Are you sure you want to ' + message + '? <b>Click</b> that button again if you\'re sure',
            status: 'warning',
        });
        queuedActions.push(name);
    } else {
        queuedActions = queuedActions.filter(action => action !== name);

        let submitObject = undefined;
        if (name === "remove-email") {
            submitObject = {'type': 'remove-email'};
        }
        else if (name === "add-email") {
            submitObject = {'type': 'add-email', 'value': $("#set-email").val()};
            UIkit.notification("Verifying your email... please wait", "primary");
        }

        update(submitObject)
    }
}

function update(submitObject) {
    $.post("/settings", JSON.stringify(submitObject), function (data) {
        if (data.status === 200) {
            if (data.redirect !== null) window.location.href = data.redirect;
            else document.location.reload(true);
        } else UIkit.notification(data.message, 'danger');
    }, "json");
}