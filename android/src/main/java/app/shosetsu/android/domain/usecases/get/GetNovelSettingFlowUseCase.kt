package app.shosetsu.android.domain.usecases.get

import app.shosetsu.android.common.ext.logE
import app.shosetsu.android.common.utils.uifactory.NovelSettingConversionFactory
import app.shosetsu.common.GenericSQLiteException
import app.shosetsu.common.domain.model.local.NovelSettingEntity
import app.shosetsu.common.domain.repositories.base.INovelSettingsRepository
import app.shosetsu.common.domain.repositories.base.INovelsRepository
import app.shosetsu.common.domain.repositories.base.ISettingsRepository
import app.shosetsu.common.view.uimodel.NovelSettingUI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.transform

/*
 * This file is part of Shosetsu.
 *
 * Shosetsu is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Shosetsu is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Shosetsu.  If not, see <https://www.gnu.org/licenses/>.
 */

/**
 * 29 / 12 / 2020
 *
 * Gets a novel setting flow, but will create a
 */
class GetNovelSettingFlowUseCase(
	private val novelSettingsRepository: INovelSettingsRepository,
	private val iSettingsRepository: ISettingsRepository,
	private val iNovelsRepository: INovelsRepository
) {
	operator fun invoke(novelID: Int): Flow<NovelSettingUI?> =
		novelSettingsRepository.getFlow(novelID).transform { settings ->
			settings?.let {
				emit(NovelSettingConversionFactory(it).convertTo())
			} ?: run {
				try {
					novelSettingsRepository.insert(NovelSettingEntity(novelID))
				} catch (e: GenericSQLiteException) {
					logE("Cannot insert, Already inserted?", e)
				}
			}
		}
}