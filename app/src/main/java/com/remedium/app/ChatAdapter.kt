package com.remedium.app

import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class ChatMessage(
    val question: String,
    val answer: String
)

class ChatAdapter(
    private val onSpeakClick: (String) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    fun addMessage(question: String, answer: String) {
        messages.add(ChatMessage(question, answer))
        notifyItemInserted(messages.size - 1)
    }

    fun getLastAnswer(): String {
        return if (messages.isNotEmpty()) messages.last().answer else ""
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val msg = messages[position]
        holder.bind(msg)
    }

    override fun getItemCount(): Int = messages.size

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val questionContainer: LinearLayout = itemView.findViewById(R.id.questionContainer)
        private val tvQuestion: TextView = itemView.findViewById(R.id.tvQuestion)
        private val answerContainer: LinearLayout = itemView.findViewById(R.id.answerContainer)
        private val tvAnswer: TextView = itemView.findViewById(R.id.tvAnswer)
        private val btnSpeakAnswer: ImageButton = itemView.findViewById(R.id.btnSpeakAnswer)

        fun bind(msg: ChatMessage) {
            tvQuestion.text = msg.question
            tvAnswer.text = msg.answer

            btnSpeakAnswer.setOnClickListener {
                onSpeakClick(msg.answer)
            }
        }
    }
}