let formQuestions = undefined;
let exists = false;

function initializeQuestions(questions) {
    formQuestions = questions;
    questions.forEach(function (questionPair) {
        let questionDiv = $("<div class='ccs-question'></div>");
        let question = questionPair.second;
        let required = "";
        if (question.required) required = "<span style='color:red;'>*</span> ";
        let questionTitle = required + "<span><b>" + question.question + "</b></span>";
        $(questionTitle).appendTo(questionDiv);


        let questionContentDiv = $("<div class='question-content uk-margin'></div>");
        if (questionPair.first < 4) {
            let qContent = undefined;
            if (questionPair.first === 3) {
                questionContentDiv = $("<fieldset style='border: none; padding-left:0;'></fieldset>");
                qContent = $("<select class='uk-select' style='max-width: 85%;'><option selected>Select..</option></select>");
            }
            else qContent = $("<div class='uk-form-controls'></div>");
            question.options.forEach(function (option) {
                let optionHtml = undefined;
                if (questionPair.first === 1) {
                    optionHtml = $("<label><input type='radio' class='uk-radio'> " + option + "</label>");
                    optionHtml.click(function () {
                            let curr = $(this);
                            curr.parent().children().each(function () {
                                    if ($(this).text() !== curr.text()) {
                                        let input = $(this).find("input").first();
                                        if (input.is(":checked")) input.prop("checked", false);
                                    }
                                }
                            )
                        }
                    );
                }

                else if (questionPair.first === 2) optionHtml = $("<label><input type='checkbox' class='uk-checkbox'> " + option + "</label>");
                else optionHtml = $("<option>" + option + "</option>");

                if (questionPair.first !== 3 && option !== question.options[question.options.length - 1]) $("<br>").appendTo(optionHtml);

                optionHtml.appendTo(qContent);
            });

            if (question.includeOtherOption) {
                qContent.append("<br>");
                let div = $("<div class='uk-inline uk-width-3-5'></div>");
                let button = $("<button type='button' class='uk-form-icon uk-form-icon-flip' uk-icon='icon: close;'></button>");
                button.click(function () {
                    if (!exists) button.parent().find("input").val("")
                });
                button.appendTo(div);
                let input = $("<input type='text' class='other-checkbox uk-margin-small-left uk-input' placeholder='Please specify'>");
                input.on("input", function (e) {
                    input.parent().parent().parent().find("input.uk-radio").prop("checked", false);
                });
                input.appendTo(div);

                let wrapper = $("<div></div>");
                $("<p class='uk-inline uk-margin-remove-bottom'>Other:</p>").appendTo(wrapper);
                div.appendTo(wrapper);

                wrapper.appendTo(qContent)
            }
            qContent.appendTo(questionContentDiv);
        }
        else if (questionPair.first === 4) {
            let wordLimit = undefined;
            if (question.wordLimit !== null) wordLimit = question.wordLimit;
            else wordLimit = "None";
            let wordLimitP = $("<p class='uk-margin-small-top' style='font-size: 10px;'>Word limit: <span style='color:lightskyblue;'>0 / " + wordLimit + "</span></p>");
            if (wordLimit === "None" || question.wordLimit > 15) {
                let textArea = $("<textarea class='uk-textarea uk-margin-remove-bottom txt uk-width-4-5' rows='4' placeholder='Enter response.. Bear the word limit in mind!'></textarea>");
                textArea.appendTo(questionContentDiv);
                textArea.on("change keyup paste", function () {
                    let color = undefined;
                    if (wordLimit === "None" || (wordLimit >= getLength($(this).val()))) color = "lightskyblue";
                    else color = "red";

                    wordLimitP.html("Word limit: <span style='color:" + color + ";'>" + getLength($(this).val()) + " / " + wordLimit + "</span>");
                });
            }
            else {
                let inputText = $("<input type='text' class='uk-input txt uk-width-4-5' placeholder='Enter answer here..'>");
                inputText.on("input", function () {
                    let color = undefined;
                    if (wordLimit === "None" || (wordLimit >= getLength($(this).val()))) color = "lightskyblue";
                    else color = "red";
                    wordLimitP.html("Word limit: <span style='color:" + color + ";'>" + getLength($(this).val()) + " / " + wordLimit + "</span>");
                });
                inputText.appendTo(questionContentDiv);
            }
            questionContentDiv.append(wordLimitP);
        }
        else {
            $("<input type='number' class='uk-input uk-margin-remove-bottom' min='" + question.minimumNumber + "' max='" + question.maximumNumber + "'" +
                " placeholder='Enter number here..'>").appendTo(questionContentDiv);
            let limitText = "";
            if (question.minimumNumber !== null) {
                limitText += "Minimum: <span style='color:lightskyblue;'>" + question.minimumNumber + "</span>"
            }
            if (question.maximumNumber !== null) {
                if (question.minimumNumber !== null) limitText += " | ";
                limitText += "Maximum: <span style='color:lightskyblue;'>" + question.maximumNumber + "</span>"
            }
            if (limitText.length > 0) $("<p class='uk-margin-small-top uk-margin-small-bottom' style='font-size: 10px;'>" + limitText + "</p>").appendTo(questionContentDiv);
            if (question.onlyWholeNumbers) {
                $("<p class='uk-margin-remove-top' style='font-size: 10px;'>Decimal numbers are <u>not</u> allowed</p>").appendTo(questionContentDiv);
            }
        }

        questionContentDiv.appendTo(questionDiv);
        questionDiv.appendTo("#questions")
    })
    ;
}

function submit() {
    let button = $($.find("button.sbm"));
    button.prop("disabled", true);
    let submit = true;
    let responses = [];
    $(".ccs-question").each(function (index) {
        let spans = $(this).find("span");
        let questionName = undefined;
        let required = false;

        let spanQuestionIndex = 0;
        if (spans.first().text() === "*") {
            required = true;
            spanQuestionIndex = 1;
        }

        questionName = spans.eq(spanQuestionIndex).find("b").text();

        let foundQuestion = getQuestion(questionName);
        if (foundQuestion === null) submit = false;
        else {

            // TODO Add red highlighting around questions if they haven't been answered correctly

            let content = $(this).find(".question-content");
            let type = foundQuestion.first;
            let question = foundQuestion.second;
            if (type === 1) {
                let checked = content.find("input:checked").first();
                let other = content.find(".other-checkbox").val();

                let value = undefined;
                if (checked.parent().text() === "" && question.includeOtherOption) value = other;
                else value = checked.parent().text().trim();

                if (value === undefined && required) {
                    notify("Question " + questionName + " needs to be answered!");
                    submit = false;
                    content.find("input").first().parent().parent().addClass("uk-form-danger");
                }
                else {
                    content.find("input").parent().parent().removeClass("uk-form-danger");
                    responses.push({"selected": value, "questionName": questionName})
                }
            } else if (type === 2) {
                let checked = content.find("input:checked").map(function () {
                    return $(this).parent().text().trim();
                }).get();
                if (question.includeOtherOption) {
                    let other = content.find(".other-checkbox").val();
                    if (other !== "") checked.push(other);
                }
                if (checked.length === 0 && required) {
                    notify("You didn't select an answer for question " + questionName);
                    submit = false;
                    content.find("input").parent().parent().addClass("uk-form-danger");
                }
                else {
                    content.find("input").parent().parent().removeClass("uk-form-danger");
                    responses.push({"selected": checked, "questionName": questionName})
                }
            } else if (type === 3) {
                content = $(this).find("fieldset").first();
                let selected = content.find("select").val().trim();
                if ((selected === "Select.." || selected === undefined) && required) {
                    notify("You didn't select an answer for question " + questionName);
                    submit = false;
                    content.find("select").addClass("uk-form-danger");
                } else {
                    content.find("select").removeClass("uk-form-danger");
                    responses.push({"selected": selected, "questionName": questionName});
                }
            } else if (type === 5) {
                let number = parseFloat(content.find("input").first().val());
                if (required) {
                    let highlight = false;
                    if (isNaN(number)) {
                        notify("You didn't enter a number for question " + questionName);
                        submit = false;
                        highlight = true;
                    } else if (question.onlyWholeNumbers && !Number.isInteger(number)) {
                        notify("You need to enter a whole number for question " + questionName);
                        submit = false;
                        highlight = true;
                    } else if ((question.minimumNumber !== null && number < question.minimumNumber)) {
                        notify("You don't meet the minimum number requirement for question " + questionName);
                        submit = false;
                        highlight = true;
                    } else if ((question.maximumNumber !== null && number > question.maximumNumber)) {
                        notify("You don't meet the maximum number requirement for question " + questionName);
                        submit = false;
                        highlight = true;
                    } else {
                        content.find("input").removeClass("uk-form-danger");
                        responses.push({"number": number, "questionName": questionName});
                    }
                    if (highlight) content.find("input").addClass("uk-form-danger");

                }
            } else if (type === 4) {
                let text = content.find(".txt").val().trim();
                let length = undefined;
                if (text === undefined) length = undefined;
                else length = getLength(text);
                let wordLimit = question.wordLimit;
                if (text === undefined || text.length === 0) {
                    if (required) {
                        notify("You need to answer question " + questionName);
                        content.find(".txt").addClass("uk-form-danger");
                        submit = false;
                    }
                }
                else {
                    if (wordLimit !== null && wordLimit < length) {
                        notify("You exceeded the word limit on question " + questionName);
                        submit = false;
                        content.find(".txt").addClass("uk-form-danger");
                    } else {
                        content.find(".txt").removeClass("uk-form-danger");
                        responses.push({"text": text, "questionName": questionName});
                    }
                }
            }
        }
    });
    if (submit) {
        let submitObject = {
            'responses': responses,
            'formId': formId
        };
        $.post("/forms/submit", JSON.stringify(submitObject), function (data) {
            if (data.status === 200) {
                window.location.replace(decodeURIComponent(data.redirect));
            }
            else {
                button.prop("disabled", false);
                notify(data.message);
            }
        }, "json")
    } else button.prop("disabled", false);
}

function initializeAnswers(dbResponse) {
    exists = true;
    let username = undefined;
    if (dbResponse.submitter === null) username = "Anonymous";
    else username = dbResponse.submitter;

    $("<h3>Submitted by <b>" + username + "</b> at <b>" + new Date(dbResponse.time).toLocaleString() + "</b>").insertAfter("#description");
    $("#submit-button").prop("disabled", true);
    $(document).find("input").prop("disabled", true);
    $(document).find("select").prop("disabled", true);
    $(document).find("textarea").prop("disabled", true);

    dbResponse.response.formQuestionAnswers.forEach(function (questionAnswer) {
        console.log(questionAnswer)
        let questionDiv = $(document).find("b").filter(function () {
            return $(this).text() === questionAnswer.questionName;
        }).first().parent().parent();

        if (questionAnswer.type === "NumberAnswer") questionDiv.find("input").val(questionAnswer.chosen);
        else if (questionAnswer.type === "TextAnswer") {
            questionDiv.find("input").val(questionAnswer.text);
            questionDiv.find("textarea").val(questionAnswer.text);
        }
        else if (questionAnswer.type === "DropboxAnswer") {
            questionDiv.find("select").val(questionAnswer.selected).prop("selected", true);
        }
        else {
            let question = getQuestion(questionAnswer.questionName).second;
            if (questionAnswer.type === "MultipleChoiceAnswer") {
                if (!question.options.includes(questionAnswer.selected)) questionDiv.find("input.other-checkbox").val(questionAnswer.selected);
                else {
                    questionDiv.find("input.uk-radio").filter(function () {
                        return $(this).parent().text().trim() === questionAnswer.selected;
                    }).prop("checked", true);
                }
            } else {
                for (let answer of questionAnswer.selected) {
                    if (!question.options.includes(answer)) questionDiv.find("input.other-checkbox").val(answer);
                    else {
                        questionDiv.find("input.uk-checkbox").filter(function () {
                            return $(this).parent().text().trim() === answer;
                        }).prop("checked", true);
                    }
                }
            }
        }
    });

    console.log(dbResponse)
}

function getQuestion(questionName) {
    for (let question of formQuestions) {
        if (question.second.question === questionName) return question;
    }
    return null;
}

function getLength(s) {
    return s.split(" ").filter(function (element) {
        return element !== ""
    }).length
}

function notify(error) {
    UIkit.notification(error, 'danger');
}