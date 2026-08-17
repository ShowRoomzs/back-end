# CLAUDE.md

Showroomz 백엔드 (Spring Boot / Gradle / Java).

## 패키지 구조

- `showroomz.api.<역할>.<기능>.{controller, docs, dto|DTO, service}` — 역할은 `admin` / `seller` / `creator` / `app`(유저) / `common` / `test`
- `showroomz.domain.<도메인>.{entity, repository, type}`
- `showroomz.global.*` — 설정, 공통 DTO, 예외

Swagger 문서는 **컨트롤러가 `XxxControllerDocs` 인터페이스를 implements** 하는 구조다. `@Tag`, `@Operation`, `@ApiResponse` 등 문서 어노테이션은 docs 인터페이스에 두고, 컨트롤러에는 매핑만 둔다.

---

## 기획 제외 범위 (작업 시 반드시 확인)

아래 기능들은 **기획에서 제외**되어 `@Hidden`으로 Swagger에서 숨겨진 상태다. 코드는 **보류 상태로 보존**하며, 되살릴지 폐기할지는 **기능별로 미정**이다.

각 대상 파일의 `@Hidden` 위에 `// [기획 제외]` 주석이 달려 있다. 전체 조회:

```
grep -rn "\[기획 제외\]" src/main/java
```

### 작업 규칙

1. **신규 기능 추가·스펙 변경 대상이 아니다.** 이 영역에 기능을 붙이지 말 것.
2. **일괄 수정·리팩터링에서 제외한다.** 코드베이스 전체를 훑어 고치는 작업(네이밍 통일, 공통화, 의존성 정리 등)을 할 때 이 파일들은 건너뛴다.
3. **테스트 작성 대상이 아니다.** 커버리지 목표나 테스트 추가 요청의 범위에 포함하지 않는다.
4. **`@Hidden`을 제거하지 않는다.** Swagger 노출은 기획 복귀가 확정된 뒤에만.
5. **삭제하지 않는다.** 보류 상태이지 폐기 확정이 아니다.
6. **다른 작업이 이 코드의 수정을 요구하는 것처럼 보이면, 진행하지 말고 먼저 사용자에게 확인한다.**

### 제외 대상 컨트롤러 (17개)

| 역할 | 경로 | 컨트롤러 |
|---|---|---|
| Admin | `/v1/admin/markets` | `api/admin/market/controller/AdminMarketController` |
| Admin | `/v1/admin` (마켓 관리) | `api/admin/market/controller/MarketAdminController` |
| Admin | `/v1/admin/social` | `api/admin/social/controller/AdminSocialController` |
| Admin | `/v1/admin/history` | `api/admin/history/controller/LoginHistoryController` |
| Admin | `/v1/admin/filters` | `api/admin/filter/controller/FilterController` |
| Admin | `/v1/admin/categories` (필터 동기화) | `api/admin/filter/controller/CategoryFilterSyncController` |
| Admin | `/v1/admin/coupons` | `api/admin/coupon/controller/AdminCouponController` |
| Seller | `/v1/seller/markets` | `api/seller/market/controller/MarketController` |
| Seller | `/v1/seller/coupons` | `api/seller/coupon/controller/SellerCouponController` |
| Seller | `/v1/seller/answer-templates` | `api/seller/inquiry/controller/AnswerTemplateController` |
| Seller | `/v1/seller/product-announcements` | `api/seller/productannouncement/controller/SellerProductAnnouncementController` |
| User | `/v1/user/coupons` | `api/app/coupon/controller/UserCouponController` |
| User | `/v1/user/products` (쿠폰) | `api/app/coupon/controller/UserProductCouponController` |
| Common | `/v1/common/products` (쿠폰) | `api/common/coupon/controller/CommonCouponController` |
| Common | `/v1/common/products` (리뷰) | `api/common/review/controller/CommonProductReviewController` |
| Common | `/v1/common/filters` | `api/common/filter/controller/CommonFilterController` |
| Common | `/v1/common/markets` | `api/common/market/controller/CommonMarketController` |

기능 묶음으로 보면 **쿠폰 전체 · 필터 전체 · 마켓(어드민/셀러/공용) · 공용 리뷰 · 로그인 이력 · 소셜 정책 관리 · 답변 템플릿 · 상품 공지사항** 이다.

### 주의: 활성 기능이 의존하는 제외 패키지

제외 대상 패키지 안에 **활성 기능이 실제로 쓰는 클래스**가 섞여 있다. 아래는 **삭제·이동·시그니처 변경 금지**:

| 제외 패키지 내 클래스 | 이를 사용하는 **활성** 코드 |
|---|---|
| `api.admin.social.service.SocialPolicyService` | `api/app/auth/service/SocialLoginService` (유저 소셜 로그인) |
| `api.seller.market.service.MarketService` | `api/seller/auth/service/SellerService` (셀러 가입) |
| `api.admin.filter.service.FilterService`, `DTO.CategoryFilterDto` | `api/admin/category/service/CategoryService` (어드민 카테고리) |
| `api.admin.history.DTO.LoginHistorySearchCondition` | `domain/history/repository/LoginHistoryRepository{Custom,Impl}` |
| `api.common.review.dto.ProductReviewSortType` | `domain/review/repository/ReviewRepository{Custom,Impl}` |

이 얽힘 때문에 제외 기능들을 별도 모듈로 물리적으로 분리하거나 삭제할 수 없다. 제외는 **API 엔드포인트 계층에만** 적용되는 개념이다.

### 도메인 계층은 제외 대상이 아니다

`domain/coupon`, `domain/filter`, `domain/history`, `domain/social`, `domain/review`, `domain/market`, `domain/productannouncement` 는 **제외 대상이 아니다.** 활성 기능(`AuthService`, `ProductService`, `SettingService`, `CategoryService` 등)이 함께 사용한다. 엔티티·리포지토리 변경은 정상 작업 범위다.

### `@Hidden`이지만 기획 제외가 아닌 것

`global/config/temp/HealthCheckController` — `GET /` (헬스체크), `GET /test/sentry-error` (Sentry 검증용). 운영/인프라 목적의 **의도된 숨김**이며 기획 제외와 무관하다. 위 규칙을 적용하지 않는다.
