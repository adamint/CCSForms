package com.adamratzman.forms.common.models

data class Form(val id: String, val creator: String, val name: String, val category: FormCategory, val submitRoles: MutableList<Role>,
                val viewResultRoles: MutableList<Role>, val viewResultUsers: MutableList<String> /* username */,
                val allowedContributors: MutableList<String> /* for future use, e.g. a teacher's class or a grade */,
                var allowMultipleSubmissions: Boolean, val creationDate: Long,
                val expireDate: Long?, var active: Boolean, val formQuestions: MutableList<FormQuestion>)

data class FormResponse(val id: String, val submitter: String, val formId: String, val formQuestionAnswers: MutableList<FormQuestionAnswer>)
data class FormQuestionAnswer(val position: Int, val response: String)

abstract class FormQuestion(val question: String, val required: Boolean)
abstract class OptionsFormQuestion(question: String, required: Boolean, val options: MutableList<String>) : FormQuestion(question, required)

class MultipleChoiceQuestion(question: String, required: Boolean, options: MutableList<String>)
    : OptionsFormQuestion(question, required, options)

class CheckboxQuestion(question: String, required: Boolean, options: MutableList<String>)
    : OptionsFormQuestion(question, required, options)

class DropboxQuestion(question: String, required: Boolean, options: MutableList<String>)
    : OptionsFormQuestion(question, required, options)

class TextQuestion(question: String, required: Boolean, val characterLimit: Int?) : FormQuestion(question, required)
class NumberQuestion(question: String, required: Boolean, val minimumNumber: Double?, val maximumNumber: Double?) : FormQuestion(question, required)

enum class TextboxType(val readable: String) {
    NUMBER("Number"), DATE("Date"), TEXT("Any text")
}

enum class FormCategory(val readable: String, val minimumPositionRequired: Int) {
    PERSONAL("Personal", Role.STUDENT.position), COUNSELING("Counseling", Role.COUNSELING.position),
    ATHLETICS("Athletics", Role.ATHLETICS.position)
}