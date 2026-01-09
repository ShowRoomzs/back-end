CREATE TABLE `admin` (
  `admin_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `modified_at` datetime(6) NOT NULL,
  `phone_number` varchar(20) DEFAULT NULL,
  `name` varchar(64) NOT NULL,
  `password` varchar(128) NOT NULL,
  `email` varchar(512) NOT NULL,
  `role_type` enum('ADMIN','GUEST','SELLER','USER') NOT NULL,
  PRIMARY KEY (`admin_id`),
  UNIQUE KEY `UKc0r9atamxvbhjjvy5j8da1kam` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `admin_refresh_token` (
  `refresh_token_seq` bigint NOT NULL AUTO_INCREMENT,
  `refresh_token` varchar(256) NOT NULL,
  `admin_email` varchar(512) NOT NULL,
  PRIMARY KEY (`refresh_token_seq`),
  UNIQUE KEY `UKlob6xik9nhf2v8qbso6ft7sec` (`admin_email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `category` (
  `order` int DEFAULT NULL,
  `category_id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT NULL,
  `icon_url` varchar(2048) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`category_id`),
  KEY `FK2y94svpmqttx80mshyny85wqr` (`parent_id`),
  CONSTRAINT `FK2y94svpmqttx80mshyny85wqr` FOREIGN KEY (`parent_id`) REFERENCES `category` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `market` (
  `market_id` bigint NOT NULL AUTO_INCREMENT,
  `seller_id` bigint NOT NULL,
  `main_category` varchar(100) DEFAULT NULL,
  `market_image_url` varchar(512) DEFAULT NULL,
  `market_url` varchar(512) DEFAULT NULL,
  `sns_link_1` varchar(512) DEFAULT NULL,
  `sns_link_2` varchar(512) DEFAULT NULL,
  `sns_link_3` varchar(512) DEFAULT NULL,
  `market_description` varchar(1000) DEFAULT NULL,
  `cs_number` varchar(255) NOT NULL,
  `market_name` varchar(255) NOT NULL,
  `market_image_status` enum('APPROVED','REJECTED','UNDER_REVIEW') DEFAULT NULL,
  PRIMARY KEY (`market_id`),
  UNIQUE KEY `UKagtyaitfan2mngy8ocdu9tle5` (`seller_id`),
  UNIQUE KEY `UKqb8gnd8e5hl8gkmv4m9nxude3` (`market_name`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `product` (
  `delivery_estimated_days` int DEFAULT NULL,
  `delivery_fee` int DEFAULT NULL,
  `delivery_free_threshold` int DEFAULT NULL,
  `is_display` bit(1) NOT NULL,
  `is_out_of_stock_forced` bit(1) NOT NULL,
  `is_recommended` bit(1) NOT NULL,
  `purchase_price` int DEFAULT NULL,
  `regular_price` int NOT NULL,
  `sale_price` int NOT NULL,
  `category_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `market_id` bigint NOT NULL,
  `product_id` bigint NOT NULL AUTO_INCREMENT,
  `product_number` varchar(50) DEFAULT NULL,
  `delivery_type` varchar(100) DEFAULT NULL,
  `seller_product_code` varchar(100) DEFAULT NULL,
  `thumbnail_url` varchar(2048) DEFAULT NULL,
  `description` text,
  `name` varchar(255) NOT NULL,
  `product_notice` json DEFAULT NULL,
  `tags` json DEFAULT NULL,
  PRIMARY KEY (`product_id`),
  UNIQUE KEY `UKo9lr7abbchek72c2xu8x0g884` (`product_number`),
  KEY `FK1mtsbur82frn64de7balymq9s` (`category_id`),
  KEY `FKnd0xf8hu7ixgw6u0do43xp2fb` (`market_id`),
  CONSTRAINT `FK1mtsbur82frn64de7balymq9s` FOREIGN KEY (`category_id`) REFERENCES `category` (`category_id`),
  CONSTRAINT `FKnd0xf8hu7ixgw6u0do43xp2fb` FOREIGN KEY (`market_id`) REFERENCES `market` (`market_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `product_image` (
  `order` int NOT NULL,
  `image_id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `url` varchar(2048) NOT NULL,
  PRIMARY KEY (`image_id`),
  KEY `FK6oo0cvcdtb6qmwsga468uuukk` (`product_id`),
  CONSTRAINT `FK6oo0cvcdtb6qmwsga468uuukk` FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `product_option` (
  `option_group_id` bigint NOT NULL,
  `option_id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`option_id`),
  KEY `FK1chbk3kb4ib2qwwitild434g4` (`option_group_id`),
  CONSTRAINT `FK1chbk3kb4ib2qwwitild434g4` FOREIGN KEY (`option_group_id`) REFERENCES `product_option_group` (`option_group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `product_option_group` (
  `option_group_id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `name` varchar(255) NOT NULL,
  PRIMARY KEY (`option_group_id`),
  KEY `FKsifetwvwtdfqltwegvv0ijt28` (`product_id`),
  CONSTRAINT `FKsifetwvwtdfqltwegvv0ijt28` FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `product_variant` (
  `is_representative` bit(1) NOT NULL,
  `regular_price` int NOT NULL,
  `sale_price` int NOT NULL,
  `stock` int NOT NULL,
  `product_id` bigint NOT NULL,
  `variant_id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`variant_id`),
  KEY `FKgrbbs9t374m9gg43l6tq1xwdj` (`product_id`),
  CONSTRAINT `FKgrbbs9t374m9gg43l6tq1xwdj` FOREIGN KEY (`product_id`) REFERENCES `product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `recent_search` (
  `searched_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `id` binary(16) NOT NULL,
  `term` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKjwtiy8gf03joqr0a7pn1ioy9j` (`user_id`),
  CONSTRAINT `FKjwtiy8gf03joqr0a7pn1ioy9j` FOREIGN KEY (`user_id`) REFERENCES `users` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `seller` (
  `created_at` datetime(6) NOT NULL,
  `modified_at` datetime(6) NOT NULL,
  `seller_id` bigint NOT NULL AUTO_INCREMENT,
  `phone_number` varchar(20) DEFAULT NULL,
  `name` varchar(64) NOT NULL,
  `password` varchar(128) NOT NULL,
  `rejection_reason` varchar(500) DEFAULT NULL,
  `email` varchar(512) NOT NULL,
  `role_type` enum('ADMIN','GUEST','SELLER','USER') NOT NULL,
  `status` enum('APPROVED','PENDING','REJECTED') NOT NULL,
  PRIMARY KEY (`seller_id`),
  UNIQUE KEY `UKcrgbovyy4gvgsum2yyb3fbfn7` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_refresh_token` (
  `refresh_token_seq` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(64) NOT NULL,
  `refresh_token` varchar(256) NOT NULL,
  PRIMARY KEY (`refresh_token_seq`),
  UNIQUE KEY `UKqca3mjxv5a1egwmn4wnbplfkt` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `users` (
  `email_verified_yn` varchar(1) NOT NULL,
  `marketing_agree` bit(1) DEFAULT NULL,
  `privacy_agree` bit(1) DEFAULT NULL,
  `service_agree` bit(1) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `modified_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL AUTO_INCREMENT,
  `birthday` varchar(10) DEFAULT NULL,
  `gender` varchar(10) DEFAULT NULL,
  `username` varchar(64) NOT NULL,
  `nickname` varchar(100) NOT NULL,
  `password` varchar(128) NOT NULL,
  `email` varchar(512) NOT NULL,
  `profile_image_url` varchar(512) DEFAULT NULL,
  `provider_type` enum('APPLE','FACEBOOK','GOOGLE','KAKAO','LOCAL','NAVER') NOT NULL,
  `role_type` enum('ADMIN','GUEST','SELLER','USER') NOT NULL,
  `name` varchar(64) DEFAULT NULL,
  `phone_number` varchar(20) DEFAULT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `UKr43af9ap4edm43mmtq01oddj6` (`username`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`)
) ENGINE=InnoDB AUTO_INCREMENT=290 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `variant_option_map` (
  `option_id` bigint NOT NULL,
  `variant_id` bigint NOT NULL,
  KEY `FKipnrov51jqfx8emcvoiv7lwfj` (`option_id`),
  KEY `FK9jg4lc8rq7ys8gkfs08lreccy` (`variant_id`),
  CONSTRAINT `FK9jg4lc8rq7ys8gkfs08lreccy` FOREIGN KEY (`variant_id`) REFERENCES `product_variant` (`variant_id`),
  CONSTRAINT `FKipnrov51jqfx8emcvoiv7lwfj` FOREIGN KEY (`option_id`) REFERENCES `product_option` (`option_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;