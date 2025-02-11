package app.shosetsu.android.viewmodel.impl

import android.app.Application
import android.graphics.Color
import android.widget.ArrayAdapter
import androidx.annotation.WorkerThread
import app.shosetsu.android.common.ext.launchIO
import app.shosetsu.android.common.ext.logD
import app.shosetsu.android.common.ext.logV
import app.shosetsu.android.domain.model.local.ColorChoiceData
import app.shosetsu.android.domain.usecases.RecordChapterIsReadUseCase
import app.shosetsu.android.domain.usecases.RecordChapterIsReadingUseCase
import app.shosetsu.android.domain.usecases.get.GetChapterPassageUseCase
import app.shosetsu.android.domain.usecases.get.GetReaderChaptersUseCase
import app.shosetsu.android.domain.usecases.get.GetReaderSettingUseCase
import app.shosetsu.android.domain.usecases.get.GetReadingMarkingTypeUseCase
import app.shosetsu.android.domain.usecases.update.UpdateReaderChapterUseCase
import app.shosetsu.android.domain.usecases.update.UpdateReaderSettingUseCase
import app.shosetsu.android.view.uimodels.model.reader.ReaderChapterUI
import app.shosetsu.android.view.uimodels.model.reader.ReaderDividerUI
import app.shosetsu.android.view.uimodels.model.reader.ReaderUIItem
import app.shosetsu.android.view.uimodels.settings.base.SettingsItemData
import app.shosetsu.android.view.uimodels.settings.dsl.*
import app.shosetsu.android.viewmodel.abstracted.AChapterReaderViewModel
import app.shosetsu.android.viewmodel.impl.settings.*
import app.shosetsu.common.consts.settings.SettingKey.*
import app.shosetsu.common.domain.model.local.NovelReaderSettingEntity
import app.shosetsu.common.domain.repositories.base.ISettingsRepository
import app.shosetsu.common.enums.MarkingType
import app.shosetsu.common.enums.MarkingType.ONSCROLL
import app.shosetsu.common.enums.MarkingType.ONVIEW
import app.shosetsu.common.enums.ReadingStatus.READ
import app.shosetsu.common.enums.ReadingStatus.READING
import com.github.doomsdayrs.apps.shosetsu.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

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
 * 06 / 05 / 2020
 *
 * TODO delete previous chapter
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChapterReaderViewModel(
	private val application: Application,
	override val settingsRepo: ISettingsRepository,
	private val loadReaderChaptersUseCase: GetReaderChaptersUseCase,
	private val loadChapterPassageUseCase: GetChapterPassageUseCase,
	private val updateReaderChapterUseCase: UpdateReaderChapterUseCase,
	private val getReaderSettingsUseCase: GetReaderSettingUseCase,
	private val updateReaderSettingUseCase: UpdateReaderSettingUseCase,
	private val getReadingMarkingType: GetReadingMarkingTypeUseCase,
	private val recordChapterIsReading: RecordChapterIsReadingUseCase,
	private val recordChapterIsRead: RecordChapterIsReadUseCase

) : AChapterReaderViewModel() {
	private val isHorizontalPageSwapping by lazy {
		settingsRepo.getBooleanFlow(ReaderHorizontalPageSwap)
	}

	/**
	 * TODO Memory management here
	 *
	 * ChapterID to the data flow for it
	 */
	private val hashMap: HashMap<Int, Flow<ByteArray?>> = hashMapOf()


	private val chaptersFlow: Flow<List<ReaderChapterUI>> by lazy {
		novelIDLive.transformLatest { nId ->
			emitAll(
				loadReaderChaptersUseCase(nId)
			)
		}
	}

	override val liveData: Flow<List<ReaderUIItem<*, *>>> by lazy {
		chaptersFlow
			.combineDividers() // Add dividers

			// Invert chapters after all processing has been done
			.combineInvert()
			.onIO()
	}

	private fun Flow<List<ReaderUIItem<*, *>>>.combineInvert(): Flow<List<ReaderUIItem<*, *>>> =
		combine(
			// Only invert if horizontalSwipe && invertSwipe are true.
			// Because who will read with an inverted vertical scroll??
			settingsRepo.getBooleanFlow(ReaderIsInvertedSwipe)
				.combine(settingsRepo.getBooleanFlow(ReaderHorizontalPageSwap)) { invertSwipe, horizontalSwipe ->
					horizontalSwipe && invertSwipe
				}
		) { listResult, b ->
			listResult.let { list ->
				if (b) {
					list.reversed()
				} else {
					list
				}
			}
		}

	private fun Flow<List<ReaderChapterUI>>.combineDividers(): Flow<List<ReaderUIItem<*, *>>> =
		combine(settingsRepo.getBooleanFlow(ReaderShowChapterDivider)) { result, value ->
			result.let {
				if (value) {
					val modified = ArrayList<ReaderUIItem<*, *>>(it)
					// Adds the "No more chapters" marker
					modified.add(modified.size, ReaderDividerUI(prev = it.last().title))

					/**
					 * Loops down the list, adding in the seperators
					 */
					val startPoint = modified.size - 2
					for (index in startPoint downTo 1)
						modified.add(
							index, ReaderDividerUI(
								(modified[index - 1] as ReaderChapterUI).title,
								(modified[index] as ReaderChapterUI).title
							)
						)

					modified
				} else {
					it
				}
			}
		}


	private val readerSettingsFlow: Flow<NovelReaderSettingEntity> by lazy {
		novelIDLive.transformLatest {
			emitAll(getReaderSettingsUseCase(it))
		}
	}

	override val liveTheme: Flow<Pair<Int, Int>> by lazy {
		settingsRepo.getIntFlow(ReaderTheme).transformLatest { id: Int ->
			settingsRepo.getStringSet(ReaderUserThemes)
				.map { ColorChoiceData.fromString(it) }
				.find { it.identifier == id.toLong() }
				?.let { (_, _, textColor, backgroundColor) ->
					_defaultForeground = textColor
					_defaultBackground = backgroundColor

					emit(textColor to backgroundColor)
				} ?: emit(Color.BLACK to Color.WHITE)

		}.onIO()
	}

	override val liveIndentSize: Flow<Int> by lazy {
		readerSettingsFlow.mapLatest { result ->
			result.paragraphIndentSize.also {
				_defaultIndentSize = it
			}
		}.onIO()
	}

	override val liveParagraphSpacing: Flow<Float> by lazy {
		readerSettingsFlow.mapLatest { result ->
			logV("Mapping latest paragraph spacing")
			result.paragraphSpacingSize.also {
				_defaultParaSpacing = it
			}
		}.onIO()
	}

	override val liveTextSize: Flow<Float> by lazy {
		settingsRepo.getFloatFlow(ReaderTextSize).mapLatest {
			_defaultTextSize = it
			it
		}.onIO()
	}

	override val liveVolumeScroll: Flow<Boolean> by lazy {
		settingsRepo.getBooleanFlow(ReaderVolumeScroll).mapLatest {
			_defaultVolumeScroll = it
			it
		}.onIO()
	}

	override val liveKeepScreenOn: Flow<Boolean> by lazy {
		settingsRepo.getBooleanFlow(ReaderKeepScreenOn).onIO()
	}

	override var currentChapterID: Int = -1

	private val novelIDLive: MutableStateFlow<Int> by lazy { MutableStateFlow(-1) }

	private var _defaultTextSize: Float = ReaderTextSize.default

	private var _defaultBackground: Int = Color.WHITE
	private var _defaultForeground: Int = Color.BLACK

	private var _defaultParaSpacing: Float = ReaderParagraphSpacing.default

	private var _defaultIndentSize: Int = ReaderIndentSize.default

	private var _defaultVolumeScroll: Boolean = ReaderVolumeScroll.default

	private var _isHorizontalReading: Boolean = ReaderHorizontalPageSwap.default

	override val defaultTextSize: Float
		get() = _defaultTextSize

	override val defaultBackground: Int
		get() = _defaultBackground

	override val defaultForeground: Int
		get() = _defaultForeground

	override val defaultParaSpacing: Float
		get() = _defaultParaSpacing

	override val defaultIndentSize: Int
		get() = _defaultIndentSize

	override val defaultVolumeScroll: Boolean
		get() = _defaultVolumeScroll

	override val isHorizontalReading: Boolean
		get() = _isHorizontalReading


	override val liveChapterDirection: Flow<Boolean> by lazy {
		isHorizontalPageSwapping.mapLatest {
			_isHorizontalReading = it
			it
		}.onIO()
	}

	override fun setNovelID(novelID: Int) {
		logV("novelID=$novelID")
		when {
			novelIDLive.value == -1 ->
				logD("Setting NovelID")
			novelIDLive.value != novelID ->
				logD("NovelID not equal, resetting")
			novelIDLive.value == novelID -> {
				logD("NovelID equal, ignoring")
				return
			}
		}
		novelIDLive.tryEmit(novelID)
	}

	@WorkerThread
	override fun getChapterPassage(readerChapterUI: ReaderChapterUI): Flow<ByteArray?> =
		hashMap.getOrPut(readerChapterUI.id) {
			flow {
				emit(loadChapterPassageUseCase(readerChapterUI))
			}
		}.onIO()

	override fun toggleBookmark(readerChapterUI: ReaderChapterUI) {
		updateChapter(
			readerChapterUI.copy(
				bookmarked = !readerChapterUI.bookmarked
			)
		)
	}

	override fun updateChapter(
		chapter: ReaderChapterUI,
	) {
		launchIO {
			updateReaderChapterUseCase(chapter)
		}
	}

	override fun updateChapterAsRead(chapter: ReaderChapterUI) {
		launchIO {
			recordChapterIsRead(chapter)
			updateReaderChapterUseCase(
				chapter.copy(
					readingStatus = READ,
					readingPosition = 0.0
				)
			)
		}
	}

	/**
	 * @param chapterUI Entity to update
	 * @param markingType What is calling this update
	 * @param readingPosition Optionally update the reading position
	 */
	private fun markAsReading(
		chapterUI: ReaderChapterUI,
		markingType: MarkingType,
		readingPosition: Double = chapterUI.readingPosition
	) = launchIO {
		settingsRepo.getBoolean(ReaderMarkReadAsReading).let { markReadAsReading ->
			/*
			 * If marking chapters that are read as reading is disabled
			 * and the chapter's readingStatus is read, return to prevent further IO.
			 */
			if (!markReadAsReading && chapterUI.readingStatus == READ) return@launchIO

			updateReaderChapterUseCase(
				chapterUI.copy(
					readingStatus = if (getReadingMarkingType() == markingType) {
						launchIO {
							recordChapterIsReading(chapterUI)
						}
						READING
					} else chapterUI.readingStatus,
					readingPosition = readingPosition
				)
			)
		}
	}


	override fun markAsReadingOnView(chapter: ReaderChapterUI) {
		markAsReading(chapter, ONVIEW)
	}

	override fun markAsReadingOnScroll(chapter: ReaderChapterUI, readingPosition: Double) {
		markAsReading(chapter, ONSCROLL, readingPosition)
	}


	override fun loadChapterCss(): Flow<String> =
		settingsRepo.getStringFlow(ReaderHtmlCss)


	override fun getSettings(): Flow<List<SettingsItemData>> =
		flow {
			// First build the universal setting interface
			emit(settings())
		}.combine(readerSettingsFlow) { settingsList, settingEntity ->
			/*
			 * Combining the universal setting flow and the readerSettingFlow
			 * Handle both results together, then transform the result, adding UI for reader specific settings
			 */

			ArrayList(settingsList).apply {
				add(floatButtonSettingData(1) {
					titleRes = R.string.paragraph_spacing
					minWhole = 0

					settingEntity.paragraphSpacingSize.let { settingValue ->
						initialWhole =
							wholeSteps.indexOfFirst { it == settingValue.toInt() }.orZero()
						val decimal: Int = ((settingValue % 1) * 100).toInt()
						initialDecimal = decimalSteps.indexOfFirst { it == decimal }.orZero()
					}

					onValueSelected { selected ->
						launchIO {

							updateReaderSettingUseCase(
								settingEntity.copy(
									paragraphSpacingSize = selected.toFloat()
								)
							)
						}
					}
				})
				add(spinnerSettingData(2) {
					val context = application.applicationContext
					titleRes = R.string.paragraph_indent
					@Suppress("CheckedExceptionsKotlin")
					arrayAdapter = ArrayAdapter(
						context,
						android.R.layout.simple_spinner_dropdown_item,
						context.resources!!.getStringArray(R.array.sizes_with_none)
					)

					spinnerValue { settingEntity.paragraphIndentSize }
					var first = true
					onSpinnerItemSelected { _, _, position, _ ->
						launchIO {
							if (first) {
								first = false
								return@launchIO
							}
							updateReaderSettingUseCase(
								settingEntity.copy(
									paragraphIndentSize = position
								)
							)
						}
					}
				})

				// Sort so the result will be ordered properly
				sortBy { it.id }
			}
		}.onIO()

	private val isScreenRotationLockedFlow = MutableStateFlow(false)

	private var _tapToScroll: Boolean = ReaderIsTapToScroll.default
	private var _userCss: String = ReaderHtmlCss.default

	override val tapToScroll: Boolean
		get() = _tapToScroll

	override val userCss: String
		get() = _userCss

	init {
		launchIO {
			settingsRepo.getBooleanFlow(ReaderIsTapToScroll).collectLatest {
				_tapToScroll = it
			}
		}
		launchIO {
			settingsRepo.getStringFlow(ReaderHtmlCss).collect {
				_userCss = it
			}
		}
	}

	override val liveIsScreenRotationLocked: Flow<Boolean>
		get() = isScreenRotationLockedFlow.onIO()

	override fun toggleScreenRotationLock() {
		isScreenRotationLockedFlow.value = !isScreenRotationLockedFlow.value
	}

	suspend fun settings(): List<SettingsItemData> = listOf(
		// Quick settings
		textSizeOption(0),
		//==paragraph indent and spacing here

		// Minor Behavior settings, these wont effect the UI too much
		tapToScrollOption(3),
		volumeScrollingOption(4),
		horizontalSwitchOption(5),
		continuousScrollOption(6),
		invertChapterSwipeOption(8),
		readerKeepScreenOnOption(9),
		showReaderDivider(10),

		// Major changes
		stringAsHtmlOption(7),
	)
}