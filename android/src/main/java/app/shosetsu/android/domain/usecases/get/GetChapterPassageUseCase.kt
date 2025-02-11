package app.shosetsu.android.domain.usecases.get

import app.shosetsu.android.view.uimodels.model.reader.ReaderChapterUI
import app.shosetsu.common.domain.repositories.base.IChaptersRepository

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
 * 10 / 06 / 2020
 */
class GetChapterPassageUseCase(
	private val iChaptersRepository: IChaptersRepository,
	private val getExt: GetExtensionUseCase,
) {
	suspend operator fun invoke(readerChapterUI: ReaderChapterUI): ByteArray? =
		iChaptersRepository.getChapter(readerChapterUI.id)?.let { chapterEntity ->
			if (chapterEntity.extensionID != null) {
				getExt(chapterEntity.extensionID!!)?.let { iExtension ->
					iChaptersRepository.getChapterPassage(iExtension, chapterEntity)
				}
			} else {
				null
			}
		}
}