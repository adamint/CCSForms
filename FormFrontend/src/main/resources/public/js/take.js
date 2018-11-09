let formQuestions = undefined;

function initializeQuestions(questions) {
    formQuestions = questions;
    questions.forEach(function (questionPair) {
        console.log(questionPair);
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
                qContent = $("<select class='uk-select' style='max-width: 85%;'></select>");
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
            })
            ;
            qContent.appendTo(questionContentDiv);
        }
        else if (questionPair.first === 4) {
            if (question.wordLimit !== undefined && question.wordLimit != null && question.wordLimit > 50) {
                $("<textarea class='uk-textarea' rows='4' placeholder='Enter response.. Bear the word limit in mind!'></textarea>>" +
                    "<p style='font-size: 10px;'>Word limit: <span style='color:lightskyblue;'>" + question.wordLimit + "</span></p>")
                    .appendTo(questionContentDiv)
            }
            else $("<input type='text' class='uk-input' placeholder='Enter answer here..'>").appendTo(questionContentDiv);
        }
        else {
            $("<input type='number' class='uk-input' min='" + question.minimumNumber + "' max='" + question.maximumNumber + "'" +
                "placeholder='Enter number here..'>").appendTo(questionContentDiv)
        }

        questionContentDiv.appendTo(questionDiv);
        questionDiv.appendTo("#questions")
    })
    ;
}

function submit() {
    let submit = true;
    let responses = [];
    $(".ccs-question").each(function (index) {
        if (submit) {
            let spans = $(this).find("span");
            let questionName = undefined;
            let required = false;

            let spanQuestionIndex = 0;
            if (spans.first().html().indexOf("<span style='color:red;'>*</span>") !== 1) {
                required = true;
                spanQuestionIndex = 1;
            }

            questionName = spans[spanQuestionIndex].find("b").text();

            let foundQuestion = getQuestion(questionName);
            if (foundQuestion === null) submit = false;
            else {
                let content = $(this).find(".question-content");
                let type = foundQuestion.first;
                let selected = undefined;
                if (type === 1) {

                }
            }

        }
    });
    if (submit) {

    }
}

function getQuestion(questionName) {
    for (let question of formQuestions) {
        if (question.second.question === questionName) return question;
    }
    return null;
}