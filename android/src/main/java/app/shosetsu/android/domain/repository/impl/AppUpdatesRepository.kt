package app.shosetsu.android.domain.repository.impl

import app.shosetsu.android.common.MissingFeatureException
import app.shosetsu.android.datasource.local.file.base.IFileCachedAppUpdateDataSource
import app.shosetsu.android.datasource.remote.base.IRemoteAppUpdateDataSource
import app.shosetsu.common.domain.model.local.AppUpdateEntity
import app.shosetsu.common.domain.repositories.base.IAppUpdatesRepository
import com.github.doomsdayrs.apps.shosetsu.BuildConfig
import kotlinx.coroutines.flow.Flow

/*
 * This file is part of shosetsu.
 *
 * shosetsu is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * shosetsu is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with shosetsu.  If not, see <https://www.gnu.org/licenses/>.
 */

/**
 * shosetsu
 * 07 / 09 / 2020
 */
class AppUpdatesRepository(
	private val iRemoteAppUpdateDataSource: IRemoteAppUpdateDataSource,
	private val iFileAppUpdateDataSource: IFileCachedAppUpdateDataSource,
) : IAppUpdatesRepository {

	override fun loadAppUpdateFlow(): Flow<AppUpdateEntity?> =
		iFileAppUpdateDataSource.updateAvaLive

	private fun compareVersion(newVersion: AppUpdateEntity): Int {
		val currentV: Int
		val remoteV: Int

		// Assuming update will return a dev update for debug
		if (BuildConfig.DEBUG) {
			currentV = BuildConfig.VERSION_NAME.substringAfter("-").toInt()
			remoteV = newVersion.version.toInt()
		} else {
			currentV = BuildConfig.VERSION_CODE
			remoteV = newVersion.versionCode
		}

		return when {
			remoteV < currentV -> {
				//println("This a future release compared to $newVersion")
				-1
			}
			remoteV > currentV -> {
				//println("Update found compared to $newVersion")
				1
			}
			remoteV == currentV -> {
				//println("This the current release compared to $newVersion")
				0
			}
			else -> 0
		}
	}

	override suspend fun loadRemoteUpdate(): AppUpdateEntity? =
		iRemoteAppUpdateDataSource.loadAppUpdate().let { appUpdateEntity ->
			val compared = compareVersion(appUpdateEntity)

			if (compared > 0) {
				iFileAppUpdateDataSource.putAppUpdateInCache(
					appUpdateEntity,
					true
				)
			}

			appUpdateEntity
		}

	override suspend fun loadAppUpdate(): AppUpdateEntity =
		iFileAppUpdateDataSource.loadCachedAppUpdate()

	override fun canSelfUpdate(): Boolean =
		iRemoteAppUpdateDataSource is IRemoteAppUpdateDataSource.Downloadable

	override suspend fun downloadAppUpdate(appUpdateEntity: AppUpdateEntity): String =
		if (iRemoteAppUpdateDataSource is IRemoteAppUpdateDataSource.Downloadable)
			iRemoteAppUpdateDataSource.downloadAppUpdate(appUpdateEntity).let { response ->
				iFileAppUpdateDataSource.saveAPK(appUpdateEntity, response).also {
					// Call GC to clean up the chunky objects
					System.gc()
				}

			}
		else throw MissingFeatureException("self update")
}