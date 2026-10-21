package com.frietsync.backend.service.employeerecord;

import com.frietsync.backend.entity.user.User;

import java.util.Collection;
import java.util.UUID;

public interface GithubActivityProvider {

    GithubActivity fetch(User user, Collection<UUID> projectIds);
}