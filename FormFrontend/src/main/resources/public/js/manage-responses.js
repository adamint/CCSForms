let rps = undefined;
let warning = [];

function init(responses) {
    rps = responses;
    let responseDiv = $("#response-cards");
    if (responses == null || responses.length === 0) {
        responseDiv.append("<p>There are no responses to this form! Share the <a href='/forms/take/" + form.id + "'>link</a>!</p>")
    } else {
        if (responses.length === 1) responseDiv.append("<h3>There is 1 response - view a summary below</h3>");
        else responseDiv.append("<h3>There are " + responses.length + " responses -- view a summary of each one below</h3>");
        responseDiv.append("<br>");

        let list = $("<div></div>");
        let counter = 0;
        responses.forEach(function (response) {
            let username = undefined;
            if (response.submitter === null) username = "Anonymous";
            else username = response.submitter;
            let title = $("<h4><b>" + (counter + 1) + ". " + username + "</b></h4>");
            let content = $("<div></div>");
            content.append("<p>Submitted at: " + new Date(response.time).toLocaleString() + "</p>");
            content.append("<p>" + response.response.formQuestionAnswers.length + " questions answered</p>");
            content.append("<p>View the <a target='_blank' href='/forms/manage/response/" + response.id + "'><b>responses</b></a></p>");
            content.append("<p><a onclick='rmq(\"" + response.id + "\")'><b>Delete</b></a> this response</p>");
            let wrapper = $("<div></div>");
            wrapper.append(title);
            wrapper.append(content);

            list.append(wrapper);
            counter++;
        });

        responseDiv.append(list);
        console.log(responses)
    }
}

function rmq(id) {
    let response = rps.find(r => r.id === id);
    if (warning.find(wr => wr.id === id) === undefined) {
        UIkit.notification({
            message: 'Are you sure you want to remove ' + response.submitter + '\'s response? Click the <b>delete</b> button again',
            status: 'warning',
        });
        warning.push(response);
    } else {
        warning = warning.filter(wr => wr.id !== id);
        $.post("/forms/manage/response/delete", JSON.stringify({'formId': form.id, 'responseId': response.id}), function (data) {
            if (data.status === 200) {
                document.location.reload(true);
            } else UIkit.notification(data.message, 'danger');
        }, "json");
    }
}