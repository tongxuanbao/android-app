package app.shosetsu.android.domain.usecases.update

import app.shosetsu.android.view.uimodels.model.DownloadUI
import app.shosetsu.common.GenericSQLiteException
import app.shosetsu.common.domain.repositories.base.IDownloadsRepository

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
 * 21 / 06 / 2020
 */
class UpdateDownloadUseCase(
	private val downloadsRepository: IDownloadsRepository,
) {
	@Throws(GenericSQLiteException::class)
	suspend operator fun invoke(downloadUI: DownloadUI) {
		downloadsRepository.update(downloadUI.convertTo())
	}
}