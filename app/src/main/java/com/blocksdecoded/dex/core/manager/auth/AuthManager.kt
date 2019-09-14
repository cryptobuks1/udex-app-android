package com.blocksdecoded.dex.core.manager.auth

import android.security.keystore.UserNotAuthenticatedException
import com.blocksdecoded.dex.App
import com.blocksdecoded.dex.core.adapter.Erc20Adapter
import com.blocksdecoded.dex.core.adapter.EthereumAdapter
import com.blocksdecoded.dex.core.manager.IAdapterManager
import com.blocksdecoded.dex.core.manager.zrx.IRelayerAdapterManager
import com.blocksdecoded.dex.core.model.AuthData
import com.blocksdecoded.dex.core.security.ISecuredStorage
import io.reactivex.subjects.PublishSubject

class AuthManager(
    private val securedStorage: ISecuredStorage
) : IAuthManager {
    override var adapterManager: IAdapterManager? = null
    override var relayerAdapterManager: IRelayerAdapterManager? = null

    override var authData: AuthData? = null
        get() = securedStorage.authData//TODO: Load via safeLoad
    
    override var authDataSignal = PublishSubject.create<Unit>()

    override val isLoggedIn: Boolean
        get() = !securedStorage.noAuthData()

    @Throws(UserNotAuthenticatedException::class)
    override fun safeLoad() {
        authData = securedStorage.authData
        authDataSignal.onNext(Unit)
    }

    @Throws(UserNotAuthenticatedException::class)
    override fun login(words: List<String>) {
        AuthData(words).let {
            securedStorage.saveAuthData(it)
            authData = it
            authDataSignal.onNext(Unit)
        }
    }

    override fun logout() {
        adapterManager?.stopKits()
        relayerAdapterManager?.clearRelayers()

        EthereumAdapter.clear(App.instance)
        Erc20Adapter.clear(App.instance)

        authData = null
    }
}