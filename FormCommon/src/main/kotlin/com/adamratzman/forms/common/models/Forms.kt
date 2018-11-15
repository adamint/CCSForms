package com.adamratzman.forms.common.models

data class Form(var id: String?, val creator: String, val name: String, val description: String, val category: FormCategory, val submitRoles: List<Role?>,
                val viewResultRoles: List<Role?>, val viewResultUsers: MutableList<String> /* username */,
                val allowedContributors: MutableList<String> /* for future use, e.g. a teacher's class or a grade */,
                var allowMultipleSubmissions: Boolean, val creationDate: Long,
                val expireDate: Long?, var active: Boolean, val formQuestions: MutableList<FormQuestion>)

data class FormResponseDatabaseWrapper(val submitter: String?, val response: FormResponse, val formId:String, val time: Long = System.currentTimeMillis())
data class FormResponse(val formId: String, val formQuestionAnswers: List<FormQuestionAnswer>)
open class FormQuestionAnswer(val questionName: String)
class MultipleChoiceAnswer(questionName: String, val selected: String) : FormQuestionAnswer(questionName)
class CheckboxAnswer(questionName: String, val selected: List<String>) : FormQuestionAnswer(questionName)
class DropboxAnswer(questionName: String, val selected: String) : FormQuestionAnswer(questionName)
class NumberAnswer(questionName: String, val chosen: Float) : FormQuestionAnswer(questionName)
class TextAnswer(questionName: String, val text: String) : FormQuestionAnswer(questionName)

abstract class FormQuestion(val question: String, val required: Boolean, @Transient val type: QuestionType)
abstract class OptionsFormQuestion(question: String, required: Boolean, type: QuestionType, val includeOtherOption: Boolean, val options: MutableList<String>) : FormQuestion(question, required, type)

class MultipleChoiceQuestion(question: String, required: Boolean, includeOtherOption: Boolean, options: MutableList<String>)
    : OptionsFormQuestion(question, required, QuestionType.MULTIPLE_CHOICE, includeOtherOption, options)

class CheckboxQuestion(question: String, required: Boolean, includeOtherOption: Boolean, options: MutableList<String>)
    : OptionsFormQuestion(question, required, QuestionType.CHECKBOX, includeOtherOption, options)

class DropboxQuestion(question: String, required: Boolean, includeOtherOption: Boolean, options: MutableList<String>)
    : OptionsFormQuestion(question, required, QuestionType.DROPBOX, includeOtherOption, options)

class TextQuestion(question: String, required: Boolean, val wordLimit: Int?) : FormQuestion(question, required, QuestionType.TEXT)
class NumberQuestion(question: String, required: Boolean, val minimumNumber: Double?, val maximumNumber: Double?,
                     val onlyWholeNumbers: Boolean) : FormQuestion(question, required, QuestionType.NUMBER)

enum class QuestionType(val readable: String) {
    MULTIPLE_CHOICE("Multiple Choice"), CHECKBOX("Checkbox"),
    DROPBOX("Dropdown"), NUMBER("Number"), TEXT("Text");

    override fun toString(): String = readable
}

enum class TextboxType(val readable: String) {
    NUMBER("Number"), DATE("Date"), TEXT("Any text")
}

enum class FormCategory(val readable: String, val minimumPositionRequired: Int) {
    PERSONAL("Personal", Role.STUDENT.position), COUNSELING("Counseling", Role.COUNSELING.position),
    ATHLETICS("Athletics", Role.ATHLETICS.position)
}