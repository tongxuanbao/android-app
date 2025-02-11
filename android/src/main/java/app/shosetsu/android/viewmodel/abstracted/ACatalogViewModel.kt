package app.shosetsu.android.viewmodel.abstracted

import app.shosetsu.android.common.utils.ColumnCalculator
import app.shosetsu.android.view.uimodels.model.catlog.ACatalogNovelUI
import app.shosetsu.android.viewmodel.base.ShosetsuViewModel
import app.shosetsu.common.enums.NovelCardType
import app.shosetsu.lib.Filter
import app.shosetsu.lib.IExtension
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
 * 01 / 05 / 2020
 * Used for showing the specific listing of a novel
 */
abstract class ACatalogViewModel :
	ShosetsuViewModel(),
	ColumnCalculator {

	/**
	 * What is currently being displayed to the user
	 */
	abstract val itemsLive: Flow<List<ACatalogNovelUI>>

	/**
	 * The list of items that will be presented as the filter menu
	 */
	abstract val filterItemsLive: Flow<List<Filter<*>>>

	/**
	 * enable or disable searching
	 */
	abstract val hasSearchLive: Flow<Boolean>

	/**
	 * Only set if [hasSearchLive] has been collected at least once
	 */
	abstract val hasSearch: Boolean

	/**
	 * Name of the extension that is used for its catalogue
	 */
	abstract val extensionName: Flow<String>

	/**
	 * What type of card to display
	 */
	abstract val novelCardTypeLive: Flow<NovelCardType>

	/**
	 * Only set if [novelCardTypeLive] has been collected at least once
	 */
	abstract val novelCardType: NovelCardType

	/**
	 * Sets the [IExtension]
	 *
	 * This will reset the view completely
	 */
	abstract fun setExtensionID(extensionID: Int)

	/**
	 * Apply a query
	 *
	 * This will reload the view
	 */
	abstract fun applyQuery(newQuery: String)

	/**
	 * Ask for more to be loaded
	 *
	 * Will append new data to [itemsLive]
	 */
	abstract fun loadMore()

	/**
	 * Resets the view back to what it was when it first opened
	 */
	abstract fun resetView()

	/**
	 * Bookmarks and loads the specific novel in the background
	 * @param novelID ID of novel to load
	 */
	abstract fun backgroundNovelAdd(novelID: Int): Flow<BackgroundNovelAddProgress>

	enum class BackgroundNovelAddProgress { ADDING, ADDED }

	/**
	 * Apply filters
	 *
	 * This will reset [itemsLive]
	 */
	abstract fun applyFilter()

	/**
	 * Reset the filter data to nothing
	 *
	 * This will reset [itemsLive]
	 */
	abstract fun resetFilter()

	abstract fun setViewType(cardType: NovelCardType)

	/**
	 * This will reset the view model completely so it can be reused later
	 */
	abstract fun destroy()


	abstract fun getFilterStringState(id: Filter<String>): Flow<String>
	abstract fun setFilterStringState(id: Filter<String>, value: String)

	abstract fun getFilterBooleanState(id: Filter<Boolean>): Flow<Boolean>
	abstract fun setFilterBooleanState(id: Filter<Boolean>, value: Boolean)


	abstract fun getFilterIntState(id: Filter<Int>): Flow<Int>
	abstract fun setFilterIntState(id: Filter<Int>, value: Int)


	/**
	 * Get the URL to open web view for the extension
	 */
	abstract fun getBaseURL(): Flow<String>

}