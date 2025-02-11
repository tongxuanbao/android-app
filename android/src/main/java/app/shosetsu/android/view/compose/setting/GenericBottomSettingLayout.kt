package app.shosetsu.android.view.compose.setting

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.sp
import com.github.doomsdayrs.apps.shosetsu.R

@Composable
fun GenericBottomSettingLayout(
	title: String,
	description: String,
	modifier: Modifier = Modifier,
	bottom: @Composable () -> Unit
) {
	Column(
		modifier = modifier,
	) {
		Column {
			Text(title)
			Text(description, fontSize = dimensionResource(R.dimen.sub_text_size).value.sp)
		}
		bottom()
	}
}