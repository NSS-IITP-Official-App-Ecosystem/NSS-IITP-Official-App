import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun Vector271(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.vector271),
        contentDescription = "Vector 271",
        modifier = modifier
                .requiredWidth(width = 510.dp)
                .requiredHeight(height = 616.dp))
 }

@Preview(widthDp = 510, heightDp = 616)
@Composable
private fun Vector271Preview() {
    Vector271(Modifier)
 }