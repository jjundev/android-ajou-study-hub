# 아주 학습 자료실 (Ajou Study Hub) Android App

아주대학교 학습 자료실 웹 애플리케이션(`https://ajou-study-hub.johnshin617.workers.dev/`)을 태블릿 및 모바일 기기에서 최적화된 학습 환경으로 제공하기 위한 전용 Android WebView 래퍼 애플리케이션입니다.

---

## 1. 프로젝트 사양 및 개요

- **애플리케이션 ID / 네임스페이스**: `com.jjundev.ajoustudyhub`
- **앱 표시 이름**: `아주 학습 자료실`
- **SDK 사양**: `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26` (Android 8.0 Oreo 이상)
- **언어 및 런타임**: Kotlin 2.1.10, Java 17 (`JavaVersion.VERSION_17`)
- **기본 운영 URL**: `https://ajou-study-hub.johnshin617.workers.dev/`

---

## 2. 핵심 아키텍처 및 구현 기능

1. **내비게이션 정책 (`NavigationPolicy`)**
   - 신뢰 호스트(`ajou-study-hub.johnshin617.workers.dev` 및 빌드 타깃 URL)의 HTTPS 내부 탐색은 인앱 WebView 내에서 연속 유지.
   - 외부 도메인 HTTPS 링크 및 사용자 제스처가 동반된 `mailto:`/`tel:` 링크는 **Chrome Custom Tabs** 또는 기본 웹 브라우저(`ACTION_VIEW`)로 안전하게 위임.
   - 비사용자 제스처 팝업 및 보안상 위험한 스키마(`http:`, `javascript:`, `file:`, `content:`, `intent:`, `data:` 등)는 전면 차단(`BLOCK`).

2. **다운로드 및 파일 핸드오프 (`DownloadTransfer` + `FileProvider`)**
   - 신뢰 호스트의 HTTPS 다운로드(`HttpURLConnection`)를 리다이렉트 홉마다 호스트 재검증하여 쿠키 유출 방지 (`blob:` 미수신).
   - 경로 순회(Path Traversal) 방지 및 HTML 응답 위장 검증을 거쳐 앱 내부 캐시 디렉터리에 안전하게 저장한 뒤, Android `FileProvider`(`content://com.jjundev.ajoustudyhub.fileprovider`) 권한을 부여하여 외부 PDF/문서 뷰어로 안전하게 핸드오프.

3. **몰입형 전체화면 (`Immersive Sticky Fullscreen`)**
   - 학습 몰입도를 극대화하기 위해 시스템 상태바 및 내비게이션 바를 기본 숨김 처리(`WindowInsetsCompat.Type.systemBars()`).
   - 시스템 가장자리 스와이프 제스처 시 일시적으로 시스템 바 표시(`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`).
   - 디스플레이 컷아웃(카메라 홀/노치) 영역까지 확장(`LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`).
   - 소프트 키보드 활성화 시 입력란을 가리지 않도록 리사이즈 처리(`adjustResize`).

4. **Android 뒤로 가기 통합 (`OnBackPressedDispatcher`)**
   - WebView 내부의 해시 탐색(`#/read/...` ↔ `#/catalog`) 및 페이지 탐색 히스토리가 남아 있는 경우 WebView 이전 페이지로 순차 복귀.
   - 첫 시작 화면(더 이상 되돌릴 이력이 없는 상태)에서 뒤로 가기를 누르면 앱 정상 종료.

5. **메인 프레임 네트워크 장애 복구**
   - 일시적 Wi-Fi 단절이나 네트워크 오류 발생 시 기본 웹뷰 흰색 에러 화면 대신 전용 재시도 오버레이 UI 표시.
   - 네트워크 연결 회복 후 "다시 시도" 버튼 클릭 시 원래 페이지 자동 복구 로드.

---

## 3. 운영 URL 동적 주입 및 오버라이드

기본 운영 URL은 `BuildConfig.STUDY_HUB_URL`에 내장되어 있으며, Gradle 빌드 속성 `-PstudyHubUrl`을 통해 스테이징 또는 테스트 URL로 간편하게 교체할 수 있습니다.

```powershell
# 기본 URL로 빌드
.\gradlew.bat :app:assembleDebug

# 특정 커스텀/스테이징 URL로 오버라이드 빌드
.\gradlew.bat :app:assembleDebug -PstudyHubUrl="https://staging.ajoustudyhub.internal/"
```

---

## 4. 빌드, 테스트 및 설치 명령어

Windows PowerShell 환경에서 아래 명령어를 실행합니다.

```powershell
# 1. 로컬 JVM 단위 테스트 실행
.\gradlew.bat :app:testDebugUnitTest

# 2. 연결된 실기기(또는 에뮬레이터) 안드로이드 계측 테스트 실행
.\gradlew.bat :app:connectedDebugAndroidTest

# 3. 디버그 APK 빌드
.\gradlew.bat :app:assembleDebug

# 4. 전체 검증 일괄 실행 (단위 테스트 + 계측 테스트 + APK 패키징)
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug

# 5. 연결된 Android 기기에 디버그 APK 설치
.\gradlew.bat :app:installDebug

# 6. 기기에서 앱 직접 실행
adb shell am start -n com.jjundev.ajoustudyhub/.MainActivity
```

---

## 5. 실기기(`TB138FC`) 인수 검증 결과

- **검증 일시**: 2026-11-14
- **대상 기기**: Lenovo Tab P11 Gen2 (`TB138FC`, Android 13 Tiramisu, 디바이스 ID: `HA1VLF8S`)

### 5.1 직접 검증 완료 항목 (`adb` / UIAutomator 검증)

| 검증 시나리오 | 검증 방법 및 명령 | 관측된 실제 결과 | 판정 |
| :--- | :--- | :--- | :---: |
| **디버그 APK 설치 및 정상 구동** | `.\gradlew.bat :app:installDebug`<br>`adb shell am start -n ...` | PID 32435로 비정상 종료/크래시(Fatal Exception) 없이 정상 실행 및 렌더링 | **PASS** |
| **몰입형 전체화면 (Immersive Mode)** | `adb shell dumpsys window windows` | `vsysui=LAYOUT_STABLE IMMERSIVE_STICKY`, `bhv=SHOW_TRANSIENT_BARS_BY_SWIPE`, `frame=[0,0][1536,2560]` 전체 화면 핏 확인 | **PASS** |
| **오프라인 감지 및 재시도 복구** | Dozing/Wi-Fi 단절 후 실행 및 복구 탭 | `net::ERR_INTERNET_DISCONNECTED` 오버레이 확인 후 "다시 시도" 버튼 탭 시 원격 카탈로그 정상 로드 | **PASS** |
| **원격 카탈로그 렌더링** | `adb shell uiautomator dump` | '아주 학습 자료실', '2026학년도 2학기 · 기기 내 학습 기록', 과목 필터 및 320개 자료 DOM 정상 표출 | **PASS** |
| **소프트 키보드 오버랩 방지** | `#catalog-search` 입력 탭 및 `0909` 타이핑 | `InputMethod` 표시(`bottom=636`, y=1924~2560) 시에도 상단 검색창(`[48,312][710,410]`) 가림 없음 | **PASS** |
| **학습 자료(iframe) 열람** | 카탈로그 내 '복습문제 문제집' 선택 | reader 컨테이너 활성화 및 내부 문제 HTML iframe("made by jjundev", "기출 1") 정상 로드 | **PASS** |
| **외부 창/새 탭 Custom Tabs 라우팅** | 외부 열기 액션 실행 | `com.android.chrome` Custom Tabs 실행, 닫기 클릭 시 기존 `MainActivity`로 무결 복귀 | **PASS** |
| **뒤로 가기(Back) 탐색** | `adb shell input keyevent KEYCODE_BACK` | `#/read/...` 상태에서 뒤로 가기 시 이전 카탈로그 뷰로 순차 복귀 | **PASS** |
| **화면 회전(Landscape/Portrait)** | `user_rotation` 0 ↔ 1 회전 테스트 | 1536x2560 (세로) ↔ 2560x1536 (가로) 회전 간 WebView 본문 유지 확인 후 기기 설정 원복 완료 | **PASS** |

### 5.2 물리적 사용자 체감 수동 확인 권장 항목 (에이전트 자동화 한계 항목)

다음 항목은 플랫폼 API/플래그 및 계측 테스트를 통해 기술적으로 구현 및 검증되었으나, 최종 릴리스 전 사람의 물리적 조작을 통한 체감 확인이 권장됩니다:
1. **물리적 가장자리 스와이프 제스처**: 태블릿 베젤 끝에서 손가락으로 쓸어내렸을 때 상태바가 반투명으로 일시 표시되었다가 다시 사라지는 UX.
2. **장기 세션 localStorage 유지**: 실제 복습문제 풀이(라디오 버튼/체크박스) 답안 입력 후 앱을 강제 종료/재실행했을 때 브라우저 로컬 스토리지 답안 유지 확인 (`domStorageEnabled=true` 적용됨).
3. **외부 전용 PDF 뷰어 선택 대화상자**: 기기에 여러 PDF 리더(Acrobat, Drive PDF 등)가 설치되어 있을 때 실제 PDF 링크 탭 시 시스템 연결 프로그램 선택 창 동작.

---

## 6. 보안 및 규정 준수

- **민감 정보 보호**: 소스 코드, 빌드 스크립트, 테스트 로그, 문서 어디에도 사용자 인증 세션 토큰, 쿠키, 민감 URL 쿼리 파라미터를 하드코딩하거나 기록하지 않습니다.
- **최소 권한 원칙**: 네트워크 통신(`INTERNET`) 외의 불필요한 위험 권한(외부 저장소 읽기/쓰기 등)을 요구하지 않으며, 파일 공유는 Android 권장 표준인 `FileProvider`의 격리된 캐시 영역을 통해서만 수행됩니다.
