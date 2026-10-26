package com.fighterhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// EnableScheduling: LoginRateLimiterの期限切れエントリ定期清掃(@Scheduled)のために有効化する。
@EnableScheduling
@SpringBootApplication
public class FighterHubApplication {

	public static void main(String[] args) {
		SpringApplication.run(FighterHubApplication.class, args);
	}

}
