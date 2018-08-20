package com.adamratzman.forms.common.models

data class Form(val id: String, val creator: String, val name: String, val category: FormCategory, val submitRoles: MutableList<Role>,
                val viewResultRoles: MutableList<Role>, val viewResultUsers: MutableList<String> /* username */,
                val allowedContributors: MutableList<String> /* for future use, e.g. a teacher's class or a grade */,
                var allowMultipleSubmissions: Boolean, val creationDate: Long,
                val expireDate: Long?, var active: Boolean, val formQuestions: MutableList<FormQuestion>)

data class FormResponse(val id: String, val submitter: String, val formId: String, val formQuestionAnswers: MutableList<FormQuestionAnswer>)
data class FormQuestionAnswer(val questionPosition: Int, val response: String)

abstract class FormQuestion(val position: Int, val title: String, val question: String)

class MultipleChoiceQuestion(position: Int, title: String, question: String, val options: MutableList<String>) : FormQuestion(position, title, question)
class CheckboxQuestion(position: Int, title: String, question: String) : FormQuestion(position, title, question)
class DropdownQuestion(position: Int, title: String, question: String, val options: MutableList<String>) : FormQuestion(position, title, question)
class TextboxQuestion(position: Int, title: String, question: String, var type: TextboxType, var allowBlank: Boolean, var minimumCharacters: Int?,
                      val options: MutableList<String>?) : FormQuestion(position, title, question)

enum class TextboxType(val readable: String) {
    NUMBER("Number"), DATE("Date"), TEXT("Any text")
}

enum class FormCategory(val readable: String, val minimumPositionRequired: Int) {
    PERSONAL("Personal", Role.STUDENT.position), COUNSELING("Counseling", Role.COUNSELING.position),
    ATHLETICS("Athletics", Role.ATHLETICS.position)
}