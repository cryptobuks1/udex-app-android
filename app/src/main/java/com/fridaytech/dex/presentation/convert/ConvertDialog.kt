package com.fridaytech.dex.presentation.convert

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.fridaytech.dex.R
import com.fridaytech.dex.core.model.EConvertType.UNWRAP
import com.fridaytech.dex.core.model.EConvertType.WRAP
import com.fridaytech.dex.presentation.common.ProcessingDialog
import com.fridaytech.dex.presentation.common.TransactionSentDialog
import com.fridaytech.dex.presentation.convert.confirm.ConvertConfirmDialog
import com.fridaytech.dex.presentation.convert.model.ConvertConfig
import com.fridaytech.dex.presentation.convert.model.ConvertState
import com.fridaytech.dex.presentation.dialogs.BaseBottomDialog
import com.fridaytech.dex.presentation.widgets.NumPadItem
import com.fridaytech.dex.presentation.widgets.NumPadItemType
import com.fridaytech.dex.presentation.widgets.NumPadItemsAdapter
import com.fridaytech.dex.presentation.widgets.click.setSingleClickListener
import com.fridaytech.dex.utils.rx.subscribeToInput
import com.fridaytech.dex.utils.ui.ToastHelper
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import java.math.BigDecimal
import kotlinx.android.synthetic.main.dialog_convert.*
import kotlinx.android.synthetic.main.view_amount_input.*

class ConvertDialog private constructor() :
    BaseBottomDialog(R.layout.dialog_convert), NumPadItemsAdapter.Listener {

    private lateinit var viewModel: ConvertViewModel
    lateinit var config: ConvertConfig

    private var inputConnection: InputConnection? = null
    private val amountChangeSubject: PublishSubject<BigDecimal> = PublishSubject.create()
    private var disposable: Disposable? = null
    private var processingDialog: DialogFragment? = null

    @SuppressLint("SetTextI18n")
    private fun updateState(state: ConvertState) {
        val action = when (state.type) {
            WRAP -> "${getString(R.string.action_wrap)} "
            UNWRAP -> "${getString(R.string.action_unwrap)} "
            else -> ""
        }

        convert_action_name?.text = action
        convert_confirm?.text = action
        convert_coin_code?.text = state.fromCoin.code

        convert_total_balance.update(state.balance, false)
        convert_coin_icon?.bind(state.fromCoin.code)
        convert_amount?.updateAmountPrefix(state.fromCoin.code)
    }

    private fun updateReceiveAmount(amount: BigDecimal) {
        if (amount > BigDecimal.ZERO) {
            convert_receive_amount?.text = amount.stripTrailingZeros().toPlainString()
        } else {
            convert_receive_amount?.text = ""
        }
    }

    private fun updateSendAmount(amount: BigDecimal) {
        if (amount > BigDecimal.ZERO) {
            amount_input?.setText(amount.stripTrailingZeros().toPlainString())
            amount_input?.setSelection(amount_input?.text?.length ?: 0)
        } else {
            amount_input?.setText("")
        }
    }

    //region Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activity?.let {
            viewModel = ViewModelProviders.of(it).get(ConvertViewModel::class.java)
            viewModel.init(config)
        }

        disposable = amountChangeSubject.subscribeToInput {
            viewModel.onAmountChanged(it)
        }

        viewModel.convertState.observe(this, Observer { state ->
            updateState(state)
        })

        viewModel.errorEvent.observe(this, Observer {
            ToastHelper.showErrorMessage(it)
        })

        viewModel.messageEvent.observe(this, Observer {
            ToastHelper.showInfoMessage(it)
        })

        viewModel.convertAmount.observe(this, Observer { amount -> updateSendAmount(amount) })

        viewModel.receiveAmount.observe(this, Observer { amount ->
            updateReceiveAmount(amount)
        })

        viewModel.convertEnabled.observe(this, Observer {
            convert_confirm?.isEnabled = it
        })

        viewModel.info.observe(this, Observer { info ->
            context?.let {
                convert_amount?.updateHint(info)
            }
        })

        viewModel.transactionSentEvent.observe(this, Observer { transactionHash ->
            if (activity != null && transactionHash != null) {
                TransactionSentDialog.show(activity!!.supportFragmentManager, transactionHash)
            }
        })

        viewModel.dismissDialog.observe(this, Observer { dismiss() })

        viewModel.showProcessingEvent.observe(this, Observer {
            processingDialog = ProcessingDialog.show(childFragmentManager)
        })

        viewModel.dismissProcessingEvent.observe(this, Observer {
            processingDialog?.dismiss()
        })

        viewModel.feeInfo.observe(this, Observer {
            convert_estimated_fee?.setCoin(it.coinCode, it.amount, isExactAmount = false)
        })

        viewModel.showConfirmEvent.observe(this, Observer { info ->
            fragmentManager?.let { ConvertConfirmDialog.show(it, info) }
        })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        convert_amount?.bindInitial(onMaxClick = {
            viewModel.onMaxClicked()
        }, onSwitchClick = {
        })

        inputConnection = amount_input?.bind(onChange = { amount ->
            convert_amount?.setMaxBtnVisible(amount <= BigDecimal.ZERO)

            amountChangeSubject.onNext(amount)
        }, decimalProvider = { viewModel.decimalSize })

        convert_numpad?.bind(this, NumPadItemType.DOT, false)

        focusInput()

        convert_confirm?.setSingleClickListener { viewModel.onConvertClick() }
    }

    //endregion

    private fun focusInput() {
        amount_input?.requestFocus()
    }

    //region Numpad

    override fun onItemClick(item: NumPadItem) {
        when (item.type) {
            NumPadItemType.NUMBER -> inputConnection?.commitText(item.number.toString(), 1)
            NumPadItemType.DELETE -> inputConnection?.deleteSurroundingText(1, 0)
            NumPadItemType.DOT -> {
                if (amount_input?.text?.toString()?.contains(".") != true) {
                    inputConnection?.commitText(".", 1)
                }
            }
        }
    }

    override fun onItemLongClick(item: NumPadItem) {
        when (item.type) {
            NumPadItemType.DELETE -> amount_input?.setText("", TextView.BufferType.EDITABLE)
        }
    }

    //endregion

    companion object {
        fun open(fragmentManager: FragmentManager, config: ConvertConfig) {
            val fragment = ConvertDialog()

            fragment.config = config

            fragment.show(fragmentManager, "convert_${config.type}")
        }
    }
}
