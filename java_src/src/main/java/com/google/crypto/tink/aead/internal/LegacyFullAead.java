// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

package com.google.crypto.tink.aead.internal;

import static com.google.crypto.tink.internal.Util.isPrefix;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.CryptoFormat;
import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.internal.LegacyProtoKey;
import com.google.crypto.tink.internal.ProtoKeySerialization;
import com.google.crypto.tink.internal.RegistryConfiguration;
import com.google.crypto.tink.proto.KeyData;
import com.google.crypto.tink.proto.OutputPrefixType;
import com.google.crypto.tink.subtle.Bytes;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Takes an arbitrary raw AEAD and makes it a full primitive. This is a class that helps us
 * transition onto the new Keys and Configurations interface, by bringing potential user-defined
 * primitives to a common denominator with our primitives over which we have control.
 */
public class LegacyFullAead implements Aead {

  private final Aead rawAead;
  private final OutputPrefixType outputPrefixType;
  private final byte[] identifier;

  /** This method covers the cases where users created their own aead/key classes. */
  public static Aead create(LegacyProtoKey key) throws GeneralSecurityException {
    ProtoKeySerialization protoKeySerialization =
        key.getSerialization(InsecureSecretKeyAccess.get());
    KeyData keyData =
        KeyData.newBuilder()
            .setTypeUrl(protoKeySerialization.getTypeUrl())
            .setValue(protoKeySerialization.getValue())
            .setKeyMaterialType(protoKeySerialization.getKeyMaterialType())
            .build();
    Aead rawPrimitive = RegistryConfiguration.get().getLegacyPrimitive(keyData, Aead.class);

    OutputPrefixType outputPrefixType = protoKeySerialization.getOutputPrefixType();
    byte[] identifier;
    switch (outputPrefixType) {
      case RAW:
        identifier = new byte[] {};
        break;
      case LEGACY:
      case CRUNCHY:
        identifier =
            ByteBuffer.allocate(CryptoFormat.NON_RAW_PREFIX_SIZE)
                .put((byte) 0)
                .putInt(key.getIdRequirementOrNull())
                .array();
        break;
      case TINK:
        identifier =
            ByteBuffer.allocate(CryptoFormat.NON_RAW_PREFIX_SIZE)
                .put((byte) 1)
                .putInt(key.getIdRequirementOrNull())
                .array();
        break;
      default:
        throw new GeneralSecurityException("unknown output prefix type " + outputPrefixType);
    }

    return new LegacyFullAead(rawPrimitive, outputPrefixType, identifier);
  }

  private LegacyFullAead(Aead rawAead, OutputPrefixType outputPrefixType, byte[] identifier) {
    this.rawAead = rawAead;
    this.outputPrefixType = outputPrefixType;
    this.identifier = identifier;
  }

  @Override
  public byte[] encrypt(byte[] plaintext, byte[] associatedData) throws GeneralSecurityException {
    if (outputPrefixType == OutputPrefixType.RAW) {
      return rawAead.encrypt(plaintext, associatedData);
    }
    return Bytes.concat(identifier, rawAead.encrypt(plaintext, associatedData));
  }

  @Override
  public byte[] decrypt(byte[] ciphertext, byte[] associatedData) throws GeneralSecurityException {
    if (outputPrefixType == OutputPrefixType.RAW) {
      return rawAead.decrypt(ciphertext, associatedData);
    }

    if (!isPrefix(identifier, ciphertext)) {
      throw new GeneralSecurityException("wrong prefix");
    }

    return rawAead.decrypt(
        Arrays.copyOfRange(ciphertext, CryptoFormat.NON_RAW_PREFIX_SIZE, ciphertext.length),
        associatedData);
  }
}
