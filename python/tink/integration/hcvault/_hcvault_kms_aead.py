# Copyright 2023 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""A client for Hashicorp Vault."""

import base64
import re
from typing import Tuple
import urllib.parse
import hvac
import tink
from tink import aead

# Matches strings like /{mount}/keys/{key_name}.
# - The initial `^/` matches strings that start with '/'
# - The string "/keys/" splits the path into two parts:
# - The first part (mount) is captured by the group ([^/]+(?:/[^/]+)*):
#   - [^/]+: Sequence of one or more characters that aren't '/', followed by
#   - (?:/[^/]+)*: Zero or more strings that start with '/' and followed by a
#     sequence of one or more characters that aren't '/'.
# - The second part (key_name) is captured by ([^/]+).
_PATH_MATCHER = re.compile(r'^/([^/]+(?:/[^/]+)*)/keys/([^/]+)$')


def _get_endpoint_paths(key_path: str) -> Tuple[str, str]:
  """Extracts mount point and key name from the given key_path.

  The key_path is expected of the form "/{mount}/keys/{key_name}". For example,
  "/transit/keys/key-foo" will be transformed to "transit" and "key-foo", and
  "/teams/billing/service/cipher/keys/key-bar" will be transformed into
  "teams/billing/service/cipher" and "key-bar".

  Args:
    key_path: Key path of the form "/{mount}/keys/{key_name}".

  Returns:
    Vault transit encryp/decrypt mount point and transit key name.
  """
  escaped_path = urllib.parse.quote(key_path)
  # Make sure that we have a path of the form: /{mount}/keys/{key_name}.
  mount_and_key_name = _PATH_MATCHER.fullmatch(escaped_path)
  if not mount_and_key_name:
    raise tink.TinkError('Invalid key_path')
  return mount_and_key_name.groups()


def new_aead(key_path: str, client: hvac.Client) -> aead.Aead:
  return _HcVaultKmsAead(client, key_path)


class _HcVaultKmsAead(aead.Aead):
  """Implements the Aead interface for Hashicorp Vault."""

  def __init__(self, client: hvac.Client, key_path: str) -> None:
    self.client = client
    mount_point, key_name = _get_endpoint_paths(key_path)
    self.key_name = key_name
    self.mount_point = mount_point

  def encrypt(self, plaintext: bytes, associated_data: bytes) -> bytes:
    try:
      response = self.client.secrets.transit.encrypt_data(
          name=self.key_name,
          plaintext=base64.urlsafe_b64encode(plaintext).decode(),
          context=base64.urlsafe_b64encode(associated_data).decode(),
          mount_point=self.mount_point,
      )
      return response['data']['ciphertext'].encode()
    except Exception as e:
      raise tink.TinkError(e)

  def decrypt(self, ciphertext: bytes, associated_data: bytes) -> bytes:
    try:
      response = self.client.secrets.transit.decrypt_data(
          name=self.key_name,
          ciphertext=ciphertext.decode(),
          context=base64.urlsafe_b64encode(associated_data).decode(),
          mount_point=self.mount_point,
      )
      return base64.urlsafe_b64decode(response['data']['plaintext'])
    except Exception as e:
      raise tink.TinkError(e)
