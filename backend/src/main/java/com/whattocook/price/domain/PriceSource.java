package com.whattocook.price.domain;

/** 가격 출처. 쿠팡·마켓컬리·B마트 등은 약관상 자동 수집이 금지되어 쓰지 않는다. */
public enum PriceSource {
    NAVER_SHOPPING, // 네이버 쇼핑 검색 API 온라인 판매가(lprice), 1일 2회 배치
    MANUAL          // 사람이 정리한 시드(seed/ingredient-prices.csv)
}
