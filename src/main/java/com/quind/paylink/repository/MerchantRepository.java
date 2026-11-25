package com.quind.paylink.repository;

import com.quind.paylink.model.Merchant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantRepository extends JpaRepository<Merchant, Long> {

    Optional<Merchant> findByApiKey(String apiKey);
}
