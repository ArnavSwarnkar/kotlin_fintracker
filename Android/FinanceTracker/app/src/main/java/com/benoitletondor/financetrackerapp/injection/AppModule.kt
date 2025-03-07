/*
 *   Copyright 2025 Benoit Letondor
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.benoitletondor.FinanceTrackerapp.injection

import android.content.Context
import com.benoitletondor.FinanceTrackerapp.accounts.Accounts
import com.benoitletondor.FinanceTrackerapp.accounts.FirebaseAccounts
import com.benoitletondor.FinanceTrackerapp.auth.Auth
import com.benoitletondor.FinanceTrackerapp.auth.CurrentUser
import com.benoitletondor.FinanceTrackerapp.auth.FirebaseAuth
import com.benoitletondor.FinanceTrackerapp.cloudstorage.CloudStorage
import com.benoitletondor.FinanceTrackerapp.cloudstorage.FirebaseStorage
import com.benoitletondor.FinanceTrackerapp.config.Config
import com.benoitletondor.FinanceTrackerapp.config.FirebaseRemoteConfig
import com.benoitletondor.FinanceTrackerapp.iab.Iab
import com.benoitletondor.FinanceTrackerapp.iab.IabImpl
import com.benoitletondor.FinanceTrackerapp.db.DB
import com.benoitletondor.FinanceTrackerapp.db.cacheimpl.CachedDBImpl
import com.benoitletondor.FinanceTrackerapp.db.cacheimpl.CachedOnlineDBImpl
import com.benoitletondor.FinanceTrackerapp.db.offlineimpl.OfflineDBImpl
import com.benoitletondor.FinanceTrackerapp.db.offlineimpl.RoomDB
import com.benoitletondor.FinanceTrackerapp.db.onlineimpl.OnlineDB
import com.benoitletondor.FinanceTrackerapp.db.onlineimpl.OnlinePGDBImpl
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideIab(
        @ApplicationContext context: Context,
    ): Iab = IabImpl(context)

    @Provides
    @Singleton
    fun provideFirebaseAuth(): Auth = FirebaseAuth(com.google.firebase.auth.FirebaseAuth.getInstance())

    @Provides
    @Singleton
    fun provideAccounts(): Accounts = FirebaseAccounts(Firebase.firestore)

    @Provides
    @Singleton
    fun provideCurrentDBProvider(): CurrentDBProvider = CurrentDBProvider(activeDB = null)

    @Provides
    @Singleton
    fun provideCloudStorage(): CloudStorage = FirebaseStorage(com.google.firebase.storage.FirebaseStorage.getInstance().apply {
        maxOperationRetryTimeMillis = TimeUnit.SECONDS.toMillis(10)
        maxDownloadRetryTimeMillis = TimeUnit.SECONDS.toMillis(10)
        maxUploadRetryTimeMillis = TimeUnit.SECONDS.toMillis(10)
    })

    @Provides
    @Singleton
    fun provideDB(
        @ApplicationContext context: Context,
    ): DB = CachedDBImpl(
        OfflineDBImpl(RoomDB.create(context)),
    )

    @Provides
    @Singleton
    fun provideConfig(): Config = FirebaseRemoteConfig()


    // Once migration to PG is done:
    // - Make sure to also remove runningFold in MainViewModel
    // - Make sure to also delete config.watchProMigratedToPgAlertMessage()

    suspend fun provideSyncedOnlineDBOrThrow(
        appContext: Context,
        currentUser: CurrentUser,
        auth: Auth,
        accountId: String,
        accountSecret: String,
        accountHasBeenMigratedToPg: Boolean,
        accounts: Accounts,
    ): OnlineDB {
        return CachedOnlineDBImpl(
            OnlinePGDBImpl.provideFor(
                currentUser = currentUser,
                auth = auth,
                accountId = accountId,
                accountSecret = accountSecret,
                appContext = appContext,
                accounts = accounts,
                accountHasBeenMigratedToPg = accountHasBeenMigratedToPg,
            )
        )
    }
}
