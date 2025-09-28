import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun Vector272(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.vector272),
        contentDescription = "Vector 272",
        modifier = modifier
                .requiredWidth(width = 371.dp)
                .requiredHeight(height = 504.dp)
                .clip(shape = RoundedCornerShape(153.dp)))
 }

@Preview(widthDp = 371, heightDp = 504)
@Composable
private fun Vector272Preview() {
    Vector272(Modifier)
 }