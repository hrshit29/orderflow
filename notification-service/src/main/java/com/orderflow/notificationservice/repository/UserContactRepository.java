package com.orderflow.notificationservice.repository;

import com.orderflow.notificationservice.model.UserContact;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserContactRepository extends JpaRepository<UserContact, Long> {
}
