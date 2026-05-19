package com.invoiceapp.activities

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.invoiceapp.R
import com.invoiceapp.models.Payment
import com.invoiceapp.network.ApiResponse
import com.invoiceapp.viewmodels.InvoiceViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class PaymentHistoryDialog : DialogFragment() {

    private lateinit var viewModel: InvoiceViewModel
    private lateinit var paymentsAdapter: PaymentsAdapter
    private var invoiceId: Int = -1
    private var onPaymentChanged: (() -> Unit)? = null
    private var currentInvoiceStatus: String? = null

    companion object {
        fun newInstance(invoiceId: Int): PaymentHistoryDialog {
            val dialog = PaymentHistoryDialog()
            val args = Bundle()
            args.putInt("invoice_id", invoiceId)
            dialog.arguments = args
            return dialog
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        invoiceId = arguments?.getInt("invoice_id") ?: -1
        viewModel = ViewModelProvider(requireActivity())[InvoiceViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_payment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView(view)
        observePayments()
        observeReversePaymentResult()
        observeReapplyPaymentResult()

        if (invoiceId != -1) {
            viewModel.getInvoicePayments(invoiceId)
            // Get current invoice status
            viewModel.invoiceDetail.observe(viewLifecycleOwner) { result ->
                if (result is ApiResponse.Success) {
                    currentInvoiceStatus = result.data.status.lowercase()
                }
            }
        }
    }

    private fun setupRecyclerView(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvPayments)
        paymentsAdapter = PaymentsAdapter { payment ->
            showPaymentActionDialog(payment)
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = paymentsAdapter

        view.findViewById<MaterialButton>(R.id.btnClose).setOnClickListener {
            dismiss()
        }
    }

    private fun observePayments() {
        viewModel.payments.observe(viewLifecycleOwner) { result ->
            when (result) {
                is ApiResponse.Loading -> {
                    // Show loading if needed
                }
                is ApiResponse.Success -> {
                    paymentsAdapter.submitList(result.data.payments)
                    updateTotalAmount(result.data.totalAmount)

                    val tvEmpty = dialog?.findViewById<TextView>(R.id.tvEmpty)
                    if (result.data.payments.isEmpty()) {
                        tvEmpty?.visibility = View.VISIBLE
                    } else {
                        tvEmpty?.visibility = View.GONE
                    }
                }
                is ApiResponse.Error -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun observeReversePaymentResult() {
        viewModel.reversePaymentResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is ApiResponse.Loading -> {
                    // Show loading
                }
                is ApiResponse.Success -> {
                    Toast.makeText(requireContext(), "Payment reversed successfully", Toast.LENGTH_SHORT).show()
                    onPaymentChanged?.invoke()
                    viewModel.getInvoicePayments(invoiceId)
                    viewModel.getInvoiceDetail(invoiceId)
                }
                is ApiResponse.Error -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun observeReapplyPaymentResult() {
        viewModel.reapplyPaymentResult.observe(viewLifecycleOwner) { result ->
            when (result) {
                is ApiResponse.Loading -> {
                    // Show loading
                }
                is ApiResponse.Success -> {
                    Toast.makeText(requireContext(), "Payment reapplied successfully", Toast.LENGTH_SHORT).show()
                    onPaymentChanged?.invoke()
                    viewModel.getInvoicePayments(invoiceId)
                    viewModel.getInvoiceDetail(invoiceId)
                }
                is ApiResponse.Error -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateTotalAmount(total: Double) {
        val tvTotalPayments = dialog?.findViewById<TextView>(R.id.tvTotalPayments)
        tvTotalPayments?.text = "Total Payments: ${formatCurrency(total)}"
    }

    private fun showPaymentActionDialog(payment: Payment) {
        // If already reversed, show option to reapply
        if (payment.status == "reversed") {
            AlertDialog.Builder(requireContext())
                .setTitle("Reversed Payment")
                .setMessage("Payment of ${formatCurrency(payment.amount)} was reversed on ${formatDateWithTime(payment.reversedAt)}.\n\nReason: ${payment.reversalReason ?: "No reason provided"}\n\nDo you want to REAPPLY this payment (undo the reversal)?")
                .setPositiveButton("Reapply Payment") { _, _ ->
                    showReapplyConfirmation(payment)
                }
                .setNegativeButton("Close", null)
                .show()
            return
        }

        // For normal payments, show reverse dialog
        showReverseConfirmation(payment)
    }

    private fun showReverseConfirmation(payment: Payment) {
        val message = if (currentInvoiceStatus == "paid") {
            "WARNING: This invoice is currently marked as PAID.\n\n" +
                    "Reversing this payment of ${formatCurrency(payment.amount)} will:\n" +
                    "• Change invoice status from PAID to PENDING\n" +
                    "• Increase balance due by ${formatCurrency(payment.amount)}\n" +
                    "• Keep a record of the reversal for audit purposes\n\n" +
                    "This is the recommended way to correct payment mistakes.\n\n" +
                    "Reason for reversal?"
        } else {
            "Reverse payment of ${formatCurrency(payment.amount)} from ${formatDate(payment.paymentDate)}?\n\n" +
                    "The payment record will be kept but marked as reversed, and the invoice balance will be updated.\n\n" +
                    "Reason for reversal?"
        }

        // Show dialog with input for reason
        val input = EditText(requireContext())
        input.hint = "Reason for reversal (required)"
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        input.setLines(3)

        AlertDialog.Builder(requireContext())
            .setTitle("Reverse Payment")
            .setMessage(message)
            .setView(input)
            .setPositiveButton("Reverse Payment") { _, _ ->
                val reason = input.text.toString().trim()
                if (reason.isEmpty()) {
                    Toast.makeText(requireContext(), "Please provide a reason for reversal", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.reversePayment(payment.id, reason)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showReapplyConfirmation(payment: Payment) {
        AlertDialog.Builder(requireContext())
            .setTitle("Reapply Payment")
            .setMessage("Are you sure you want to REAPPLY this payment?\n\n" +
                    "Amount: ${formatCurrency(payment.amount)}\n" +
                    "Original reversal reason: ${payment.reversalReason ?: "No reason"}\n\n" +
                    "This will:\n" +
                    "• Change the payment status back to COMPLETED\n" +
                    "• Update the invoice balance\n" +
                    "• Restore the invoice status if appropriate")
            .setPositiveButton("Yes, Reapply") { _, _ ->
                viewModel.reapplyPayment(payment.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatCurrency(amount: Double): String {
        val format = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))
        format.currency = Currency.getInstance("ZAR")
        return format.format(amount)
    }

    private fun formatDate(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            val date = inputFormat.parse(dateString)
            outputFormat.format(date)
        } catch (e: Exception) {
            dateString
        }
    }

    private fun formatDateWithTime(dateString: String?): String {
        if (dateString.isNullOrEmpty()) return "Unknown date"
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            val outputFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US)
            val date = inputFormat.parse(dateString)
            outputFormat.format(date)
        } catch (e: Exception) {
            dateString
        }
    }

    fun setOnPaymentChanged(callback: () -> Unit) {
        this.onPaymentChanged = callback
    }

    class PaymentsAdapter(
        private val onPaymentClick: (Payment) -> Unit
    ) : RecyclerView.Adapter<PaymentsAdapter.ViewHolder>() {

        private var payments = listOf<Payment>()

        fun submitList(newList: List<Payment>) {
            payments = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_payment, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(payments[position], onPaymentClick)
        }

        override fun getItemCount(): Int = payments.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvPaymentNumber: TextView = itemView.findViewById(R.id.tvPaymentNumber)
            private val tvPaymentDate: TextView = itemView.findViewById(R.id.tvPaymentDate)
            private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
            private val tvPaymentMethod: TextView = itemView.findViewById(R.id.tvPaymentMethod)
            private val tvReference: TextView = itemView.findViewById(R.id.tvReference)
            private val tvPaymentStatus: TextView = itemView.findViewById(R.id.tvPaymentStatus)
            private val tvReversalReason: TextView = itemView.findViewById(R.id.tvReversalReason)
            private val btnAction: MaterialButton = itemView.findViewById(R.id.btnDeletePayment)

            fun bind(payment: Payment, onPaymentClick: (Payment) -> Unit) {
                tvPaymentNumber.text = payment.paymentNumber
                tvPaymentDate.text = formatDate(payment.paymentDate)
                tvAmount.text = formatCurrency(payment.amount)
                tvPaymentMethod.text = payment.paymentMethod.uppercase()

                if (payment.referenceNumber.isNullOrEmpty()) {
                    tvReference.visibility = View.GONE
                } else {
                    tvReference.visibility = View.VISIBLE
                    tvReference.text = "Ref: ${payment.referenceNumber}"
                }

                // Show status for reversed payments
                if (payment.status == "reversed") {
                    tvPaymentStatus.visibility = View.VISIBLE
                    btnAction.text = "Reapply"
                    btnAction.setIcon(null)

                    if (!payment.reversalReason.isNullOrEmpty()) {
                        tvReversalReason.visibility = View.VISIBLE
                        tvReversalReason.text = "Reversal reason: ${payment.reversalReason}"
                    } else {
                        tvReversalReason.visibility = View.GONE
                    }
                } else {
                    tvPaymentStatus.visibility = View.GONE
                    tvReversalReason.visibility = View.GONE
                    btnAction.text = "Reverse"
                    btnAction.setIconResource(android.R.drawable.ic_menu_delete)
                }

                btnAction.setOnClickListener {
                    onPaymentClick(payment)
                }
            }

            private fun formatCurrency(amount: Double): String {
                val format = NumberFormat.getCurrencyInstance(Locale("en", "ZA"))
                format.currency = Currency.getInstance("ZAR")
                return format.format(amount)
            }

            private fun formatDate(dateString: String): String {
                return try {
                    val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                    val date = inputFormat.parse(dateString)
                    outputFormat.format(date)
                } catch (e: Exception) {
                    dateString
                }
            }
        }
    }
}