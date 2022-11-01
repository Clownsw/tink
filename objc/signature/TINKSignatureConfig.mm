/**
 * Copyright 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **************************************************************************
 */

#import "TINKSignatureConfig.h"

#import "TINKRegistryConfig.h"
#import "util/TINKErrors.h"

#include "tink/signature/signature_config.h"
#include "tink/util/status.h"

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wobjc-designated-initializers"
@implementation TINKSignatureConfig

- (nullable instancetype)initWithError:(NSError **)error {
  auto st = crypto::tink::SignatureConfig::Register();
  if (!st.ok()) {
    if (error) {
      *error = TINKStatusToError(st);
    }
    return nil;
  }

  return (self = [super initWithError:error]);
}

@end
#pragma clang diagnostic pop
