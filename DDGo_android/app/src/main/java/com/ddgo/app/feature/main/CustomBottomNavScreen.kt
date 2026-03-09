
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun CustomBottomNavScreen() {
    Scaffold(
        bottomBar = {
            BottomAppBar(
                containerColor = Color.White,
                contentColor = Color.Gray,
                tonalElevation = 8.dp,
                contentPadding = PaddingValues(0.dp)
            ) {
                // 하단바 아이템들을 가로로 배치
                Row(
                    modifier = Modifier.fillMaxWidth().height(65.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { /*TODO*/ }) {
                        Icon(Icons.Filled.DateRange, contentDescription = "캘린더")
                    }
                    IconButton(onClick = { /*TODO*/ }) {
                        Icon(Icons.Filled.ChatBubbleOutline, contentDescription = "커뮤니티")
                    }

                    // 가운데 빈 공간 (클라이밍 버튼이 들어갈 자리)
                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(onClick = { /*TODO*/ }) {
                        Icon(Icons.Filled.BarChart, contentDescription = "분석")
                    }
                    IconButton(onClick = { /*TODO*/ }) {
                        Icon(Icons.Filled.PersonOutline, contentDescription = "프로필")
                    }
                }
            }
        },
        floatingActionButton = {
            // 이미지처럼 파란 버튼 뒤에 하얀 둥근 배경 효과를 주기 위해 Box 사용
            Box(
                modifier = Modifier
                    .size(86.dp) // 하얀 배경의 전체 크기
                    .background(Color.White, CircleShape)
                    .padding(8.dp), // 하얀 테두리 두께 역할
                contentAlignment = Alignment.Center
            ) {
                FloatingActionButton(
                    onClick = { /*TODO*/ },
                    shape = CircleShape,
                    containerColor = Color(0xFF42A5F5), // 파란색 클라이밍 버튼
                    contentColor = Color.White,
                    modifier = Modifier.size(70.dp),
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp) // 그림자 제거로 일체감 향상
                ) {
                    // 클라이밍 아이콘 (여기서는 임시로 Add 아이콘 사용)
                    Icon(Icons.Filled.Add, contentDescription = "클라이밍", modifier = Modifier.size(36.dp))
                }
            }
        },
        // FAB를 정중앙에 배치하여 하단바에 걸치게 만듦
        floatingActionButtonPosition = FabPosition.Center
    ) { paddingValues ->
        // 메인 콘텐츠 영역
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(Color(0xFFF8F9FA))
        ) {
            Text("메인 화면 콘텐츠", modifier = Modifier.align(Alignment.Center))
        }
    }
}

// 이 부분이 빌드 없이 미리보기를 가능하게 해줍니다.
@Preview(showBackground = true)
@Composable
fun PreviewCustomBottomNavScreen() {
    CustomBottomNavScreen()
}