var questions = [];
var currId = 0;

var mcType = {id: "mc", readable: "Multiple Choice"};
var checkType = {id: "ch", readable: "Checkbox"};
var dropType = {id: "drop", readable: "Dropbox (Selection)"};
var textType = {id: "txt", readable: "Text Input"};
var numType = {id: "num", readable: "Number Input"};

var types = [mcType, checkType, dropType, textType, numType];

function createFormInitialValidation() {
    var submit = true;

    var formName = $("#name");
    var anyoneSubmit = $("#submit-anyone");
    var studentSubmit = $("#submit-students");
    var teacherSubmit = $("#submit-teachers");

    if (formName.val().length < 4) {
        submit = false;
        formName.addClass("uk-form-danger");
        UIkit.notification('The form name must be at least 4 characters', 'danger');
    } else formName.removeClass("uk-form-danger");

    if (anyoneSubmit.prop('checked') === false &&
        studentSubmit.prop('checked') === false &&
        teacherSubmit.prop('checked') === false) {
        submit = false;
        UIkit.notification("You need to allow submissions from at least one group!", 'danger');
    }

    if (submit) {
        var date = flatpickr.parseDate($("#date-selector").val());
        var millis = undefined;
        if (date === undefined) millis = undefined; else millis = date.getTime();
        initializeQuestionCreation();
    }
}

function initializeQuestionCreation() {
    $("#continue-to-questions").remove();
    var questionDiv = $("#question-div");
    questionDiv.append("<div id='questions'></div>" +
        "<a href='#question-" + currId + "' uk-icon=\"icon: plus-circle; ratio: 3;\" onclick='onNewQuestionClick()' class=\"uk-align-right\"></a>")
    var doneButton = $("<button class='uk-button uk-button-primary' form=''>Create your form</button>");
    doneButton.click(function () {
        verifyFormCompletion();
    });
    questionDiv.append(doneButton);
}

function onNewQuestionClick() {
    var type = textType; // default type is mc
    var questionDiv = $("<div id='question-" + currId +
        "' class=\"uk-card uk-card-default uk-card-body uk-margin-medium-bottom " +
        "question-div\"></div>");
    questionDiv.appendTo($("#questions"));
    renderQuestionBox(questionDiv);
    currId++;
}

function renderQuestionBox(questionDiv) {
    var badge = $("<div class=\"uk-card-badge\"></div>");
    badge.appendTo(questionDiv);

    var selectType = $("<select class='uk-select'>" +
        "<option value='1'>" + mcType.readable + "</option>" +
        "<option value='2'>" + checkType.readable + "</option>" +
        "<option value='3'>" + dropType.readable + "</option>" +
        "<option value='4'>" + textType.readable + "</option>" +
        "<option value='5'>" + numType.readable + "</option>" +
        "</select>");

    selectType.change(function () {
        appendQuestionCreation(questionDiv)
    });
    selectType.appendTo(badge);

    questionDiv.append("<h3 class=\"uk-card-title\"></h3>");
    questionDiv.append("<div class='uk-margin'>" +
        "<label class='uk-form-label' for='question-" + currId + "-q'>Question text</label>" +
        "<div class='uk-form-controls'>" +
        "<textarea class='uk-textarea' id='question-" + currId + "-q'></textarea></div></div>");

    questionDiv.append("<div class=\"content\"></div>");
    questionDiv.append("<div class=\"uk-card-footer\">" +
        "<a onclick=\"questionRemove('" + questionDiv.attr("id") + "')\" class=\"uk-button uk-button-text\">Delete</a>\n" +
        "</div>");
    appendQuestionCreation(questionDiv);
}

function appendQuestionCreation(questionDiv) {
    var type = getTypeByNumberString(questionDiv.find("select").first().val());
    questionDiv.find(".uk-card-title").first().text(type.readable);
    var content = questionDiv.find(".content").first();
    content.empty();

    if (type.readable === textType.readable) {
        content.append("<p>Character limit: " +
            "<input class='uk-input' type='number'></p>");
        content.append("<p><i>Tip: keep this blank to have no limit</i></p>");
    } else if (type.readable === numType.readable) {
        content.append("<p>Minimum number: " +
            "<input class='uk-input ccs-min-num' type='number'></p>");
        content.append("<p>Maximum number: " +
            "<input class='uk-input ccs-max-num' type='number'></p>");
        content.append("<p><i>Tip: keep these blank to have no number restrictions</i></p>");
    } else {
        content.append("<table class=\"uk-table uk-table-hover uk-table-divider\">" +
            "<thead><tr>" +
            "<th>Name</th><th>Remove</th></tr></thead>" +
            "<tbody></tbody></table>");
        var options = [];
        var input = $("<input class='uk-input uk-margin-small-bottom' type='text'>");
        var addButton = $("<a class='uk-button uk-button-primary'>Add Option</a>");
        addButton.click(function () {
            var text = input.val();
            if (text.length < 2) UIkit.notification("Option must be 2 or more characters", 'danger');
            else if (options.includes(text)) UIkit.notification("Option has already been added", 'danger');
            else {
                options.push(text);
                var tr = $("<tr><td>" + text + "</td><td><span uk-icon='icon: close'></span></td></tr>");
                tr.find("span").first().click(function () {
                    options = options.filter(function (value) {
                        return value !== $(this).prev().text()
                    });
                    tr.remove();
                });
                content.find("tbody").first().append(tr);
            }
            input.val('');
        });

        content.append("<p>Option name: ");
        content.append(input);
        content.append(addButton);
        content.append("</p>");
    }
}

function getTypeByNumberString(typeNum) {
    return {
        '1': function () {
            return mcType;
        },
        '2': function () {
            return checkType;
        },
        '3': function () {
            return dropType;
        },
        '4': function () {
            return textType;
        },
        '5': function () {
            return numType;
        }
    }[typeNum]();
}

function questionRemove(questionId) {
    $("#" + questionId).remove();
}

function verifyFormCompletion() {
    var formName = $("#name");
    var allowMultipleSubmissions = $("#multiple-submissions")

}