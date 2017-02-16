/*******************************************************************************
 * PGPTool is a desktop application for pgp encryption/decryption
 * Copyright (C) 2017 Sergey Karpushin
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *******************************************************************************/
package org.pgptool.gui.encryption.implpgp;

import java.security.Security;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.pgptool.gui.config.api.ConfigRepository;
import org.pgptool.gui.encryption.api.KeyGeneratorService;
import org.pgptool.gui.encryption.api.KeyRingService;
import org.pgptool.gui.encryption.api.dto.Key;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.summerb.approaches.jdbccrud.api.dto.EntityChangedEvent;

import com.google.common.base.Preconditions;
import com.google.common.eventbus.EventBus;

public class KeyRingServicePgpImpl implements KeyRingService<KeyDataPgp> {
	private static Logger log = Logger.getLogger(KeyRingServicePgpImpl.class);

	private ConfigRepository configRepository;
	private EventBus eventBus;
	private KeyGeneratorService<KeyDataPgp> keyGeneratorService;

	private PgpKeysRing pgpKeysRing;

	static {
		Security.addProvider(new BouncyCastleProvider());
	}

	/**
	 * This method is created to ensure static constructor of this class was
	 * called
	 */
	public static synchronized void touch() {

	}

	public KeyRingServicePgpImpl() {
	}

	@Override
	public synchronized List<Key<KeyDataPgp>> readKeys() {
		ensureRead();
		// NOTE: Return copy of the list!
		return new ArrayList<>(pgpKeysRing);
	}

	private void ensureRead() {
		if (pgpKeysRing != null) {
			return;
		}

		synchronized (this) {
			if (pgpKeysRing != null) {
				return;
			}
			pgpKeysRing = configRepository.readOrConstruct(PgpKeysRing.class);
			dumpKeys();
			if (pgpKeysRing.size() == 0) {
				keyGeneratorService.expectNewKeyCreation();
			}
		}
	}

	private void dumpKeys() {
		if (!log.isDebugEnabled()) {
			return;
		}

		for (Key<KeyDataPgp> key : pgpKeysRing) {
			log.debug("KEY --- " + key);
			if (key.getKeyData().getSecretKeyRing() != null) {
				PGPSecretKeyRing skr = key.getKeyData().getSecretKeyRing();

				log.debug("SECRET KEYRING: FIRST Public Key");
				logPublicKey(skr.getPublicKey());

				log.debug("SECRET KEYRING: ITERATING Public Keys");
				for (Iterator<PGPPublicKey> iterPK = skr.getPublicKeys(); iterPK.hasNext();) {
					PGPPublicKey pk = iterPK.next();
					logPublicKey(pk);
				}

				log.debug("SECRET KEYRING: FIRST Secret Key");
				logSecretKey(skr.getSecretKey());

				log.debug("SECRET KEYRING: ITERATING Secret Keys");
				for (Iterator<PGPSecretKey> iterPK = skr.getSecretKeys(); iterPK.hasNext();) {
					PGPSecretKey pk = iterPK.next();
					logSecretKey(pk);
				}
			} else {
				PGPPublicKeyRing pkr = key.getKeyData().getPublicKeyRing();

				log.debug("PUBLIC KEYRING: FIRST Public Key");
				logPublicKey(pkr.getPublicKey());

				log.debug("PUBLIC KEYRING: ITERATING Public Keys");
				for (Iterator<PGPPublicKey> iterPK = pkr.getPublicKeys(); iterPK.hasNext();) {
					PGPPublicKey pk = iterPK.next();
					logPublicKey(pk);
				}
			}
		}
	}

	private void logPublicKey(PGPPublicKey k) {
		String id = KeyDataPgp.buildKeyIdStr(k.getKeyID());
		String user = k.getUserIDs().hasNext() ? (String) k.getUserIDs().next() : "noUser";
		log.debug("... public key ID = " + id + ", isEncryption = " + k.isEncryptionKey() + ", isMaster = "
				+ k.isMasterKey() + ", user = " + user);
	}

	private void logSecretKey(PGPSecretKey k) {
		String id = KeyDataPgp.buildKeyIdStr(k.getKeyID());
		String user = k.getUserIDs().hasNext() ? (String) k.getUserIDs().next() : "noUser";
		log.debug("... secret key ID = " + id + ", isPrivateKeyEmpty = " + k.isPrivateKeyEmpty() + ", isSigningKey = "
				+ k.isSigningKey() + ", isMaster = " + k.isMasterKey() + ", user = " + user);
	}

	@Override
	public synchronized void addKey(Key<KeyDataPgp> key) {
		Preconditions.checkArgument(key != null, "key required");
		Preconditions.checkArgument(key.getKeyData() != null, "key data required");
		Preconditions.checkArgument(key.getKeyData() instanceof KeyDataPgp, "Wrong key data type");

		Key<KeyDataPgp> existingKey = findKeyById(key.getKeyInfo().getKeyId());
		if (existingKey != null) {
			if (!existingKey.getKeyData().isCanBeUsedForDecryption() && key.getKeyData().isCanBeUsedForDecryption()) {
				removeKey(existingKey);
			} else {
				throw new RuntimeException("This key was already added");
			}
		}

		pgpKeysRing.add(key);
		configRepository.persist(pgpKeysRing);
		eventBus.post(EntityChangedEvent.added(key));
	}

	@Override
	public synchronized Key<KeyDataPgp> findKeyById(String keyId) {
		Preconditions.checkArgument(StringUtils.hasText(keyId), "KeyId must be provided");
		ensureRead();

		for (Key<KeyDataPgp> cur : pgpKeysRing) {
			if (cur.getKeyInfo().getKeyId().equals(keyId)) {
				return cur;
			}
		}
		return null;
	}

	@Override
	public synchronized void removeKey(Key<KeyDataPgp> key) {
		ensureRead();
		for (Iterator<Key<KeyDataPgp>> iter = pgpKeysRing.iterator(); iter.hasNext();) {
			Key<KeyDataPgp> cur = iter.next();
			if (cur.getKeyInfo().getKeyId().equals(key.getKeyInfo().getKeyId())) {
				iter.remove();
				configRepository.persist(pgpKeysRing);
				eventBus.post(EntityChangedEvent.removedObject(key));
				return;
			}
		}
	}

	public ConfigRepository getConfigRepository() {
		return configRepository;
	}

	@Autowired
	public void setConfigRepository(ConfigRepository configRepository) {
		this.configRepository = configRepository;
	}

	public EventBus getEventBus() {
		return eventBus;
	}

	@Autowired
	public void setEventBus(EventBus eventBus) {
		this.eventBus = eventBus;
	}

	/**
	 * keyIds passed here MIGHT NOT match key id from keyInfo
	 */
	@Override
	public List<Key<KeyDataPgp>> findMatchingDecryptionKeys(Set<String> keysIds) {
		Preconditions.checkArgument(!CollectionUtils.isEmpty(keysIds));

		List<Key<KeyDataPgp>> ret = new ArrayList<>(keysIds.size());
		List<Key<KeyDataPgp>> allKeys = readKeys();
		List<Key<KeyDataPgp>> decryptionKeys = allKeys.stream().filter(x -> x.getKeyData().isCanBeUsedForDecryption())
				.collect(Collectors.toList());

		for (String neededKeyId : keysIds) {
			log.debug("Trying to find decryption key by id: " + neededKeyId);
			for (Iterator<Key<KeyDataPgp>> iter = decryptionKeys.iterator(); iter.hasNext();) {
				Key<KeyDataPgp> existingKey = iter.next();
				String user = existingKey.getKeyInfo().getUser();
				if (existingKey.getKeyData().isHasAlternativeId(neededKeyId)) {
					log.debug("Found matching key: " + user);
					ret.add(existingKey);
					break;
				}
			}
		}
		return ret;
	}

	@Override
	public List<Key<KeyDataPgp>> findMatchingKeys(Set<String> keysIds) {
		Preconditions.checkArgument(!CollectionUtils.isEmpty(keysIds));

		List<Key<KeyDataPgp>> ret = new ArrayList<>(keysIds.size());
		List<Key<KeyDataPgp>> allKeys = readKeys();

		for (String neededKeyId : keysIds) {
			log.debug("Trying to find key by id: " + neededKeyId);
			for (Iterator<Key<KeyDataPgp>> iter = allKeys.iterator(); iter.hasNext();) {
				Key<KeyDataPgp> existingKey = iter.next();
				String user = existingKey.getKeyInfo().getUser();
				if (existingKey.getKeyData().isHasAlternativeId(neededKeyId)) {
					log.debug("Found matching key: " + user);
					ret.add(existingKey);
					break;
				}
			}
		}
		return ret;
	}

	public KeyGeneratorService<KeyDataPgp> getKeyGeneratorService() {
		return keyGeneratorService;
	}

	@Autowired
	public void setKeyGeneratorService(KeyGeneratorService<KeyDataPgp> keyGeneratorService) {
		this.keyGeneratorService = keyGeneratorService;
	}

}
