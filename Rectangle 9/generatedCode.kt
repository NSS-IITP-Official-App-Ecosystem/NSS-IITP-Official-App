import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun Rectangle9(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.rectangle9),
        contentDescription = "Rectangle 9",
        modifier = modifier
                .requiredWidth(width = 440.dp)
                .requiredHeight(height = 497.dp))
 }

@Preview(widthDp = 440, heightDp = 497)
@Composable
private fun Rectangle9Preview() {
    Rectangle9(Modifier)
 }