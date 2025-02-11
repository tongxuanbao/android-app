package app.shosetsu.android.domain.usecases.get

import app.shosetsu.common.domain.model.local.InstalledExtensionEntity
import app.shosetsu.common.domain.repositories.base.IExtensionsRepository
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
 * 04 / 07 / 2020
 */
class GetExtensionUIUseCase(
	private val iExtensionsRepository: IExtensionsRepository,
) {
	operator fun invoke(id: Int): Flow<InstalledExtensionEntity?> =
		iExtensionsRepository.getInstalledExtensionFlow(id)
}