/*
 * Copyright 2021 YugaByte, Inc. and Contributors
 *
 * Licensed under the Polyform Free Trial License 1.0.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://github.com/YugaByte/yugabyte-db/blob/master/licenses/POLYFORM-FREE-TRIAL-LICENSE-1.0.0.txt
 */

package com.yugabyte.yw.forms;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import io.swagger.annotations.ApiModelProperty;
import play.libs.Json;
import play.mvc.Result;

import java.util.List;
import java.util.UUID;

import static play.mvc.Results.ok;

public class YWResults {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class YWStructuredError {
    public final boolean success = false;

    public final JsonNode error;

    public YWStructuredError(JsonNode err) {
      error = err;
    }
  }

  static class OkResult {
    public Result asResult() {
      return ok(Json.toJson(this));
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class YWSuccess extends OkResult {
    public final boolean success;
    public final String message;

    YWSuccess() {
      this(true, null);
    }

    YWSuccess(boolean success, String message) {
      this.success = success;
      this.message = message;
    }

    public static Result empty() {
      return new YWSuccess().asResult();
    }

    public static Result withMessage(String message) {
      return new YWSuccess(true, message).asResult();
    }
  }

  public static class YWTask extends OkResult {
    @VisibleForTesting public UUID taskUUID;

    @ApiModelProperty(
        value = "UUID of the resource being modified  by the task",
        accessMode = ApiModelProperty.AccessMode.READ_ONLY)
    @VisibleForTesting
    public UUID resourceUUID;

    // for json deserialization
    public YWTask() {}

    public YWTask(UUID taskUUID) {
      this(taskUUID, null);
    }

    public YWTask(UUID taskUUID, UUID resourceUUID) {
      this.taskUUID = taskUUID;
      this.resourceUUID = resourceUUID;
    }
  }

  public static class YWTasks extends OkResult {
    // TODO Need to make it YWTask list w/o making ui unhappy.
    public final List<UUID> taskUUID;

    public YWTasks(List<UUID> taskUUID) {
      this.taskUUID = taskUUID;
    }
  }
}
