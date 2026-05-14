package com.invoiceapp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.invoiceapp.databinding.ItemCommunicationBinding
import com.invoiceapp.models.InvoiceCommunication
import java.text.SimpleDateFormat
import java.util.*

class CommunicationAdapter(
    private var communications: List<InvoiceCommunication>,
    private val onReplyClick: (InvoiceCommunication) -> Unit
) : RecyclerView.Adapter<CommunicationAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCommunicationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(communications[position])
    }

    override fun getItemCount(): Int = communications.size

    fun updateList(newList: List<InvoiceCommunication>) {
        communications = newList
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemCommunicationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(communication: InvoiceCommunication) {
            val sentDate = try {
                dateFormat.format(SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(communication.sentAt))
            } catch (e: Exception) {
                communication.sentAt
            }

            binding.tvType.text = when (communication.communicationType) {
                "sent" -> "📧 Sent"
                "follow_up" -> "⚠️ Follow-up"
                "reply" -> "💬 Reply"
                else -> "📧 Message"
            }

            binding.tvSubject.text = communication.subject
            binding.tvMessage.text = communication.messageBody
            binding.tvDate.text = sentDate

            // Show reply indicator if customer replied
            if (communication.customerReplied && communication.replyBody != null) {
                binding.tvReplyIndicator.visibility = ViewGroup.VISIBLE
                binding.tvReplyPreview.text = "Customer replied: ${communication.replyBody?.take(100)}..."
                binding.btnReply.visibility = ViewGroup.GONE
            } else {
                binding.tvReplyIndicator.visibility = ViewGroup.GONE
                binding.btnReply.visibility = if (communication.communicationType != "reply") ViewGroup.VISIBLE else ViewGroup.GONE
                binding.btnReply.setOnClickListener {
                    onReplyClick(communication)
                }
            }
        }
    }
}