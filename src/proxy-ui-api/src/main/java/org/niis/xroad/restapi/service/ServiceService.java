/**
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.conf.globalconf.GlobalGroupInfo;
import ee.ria.xroad.common.conf.globalconf.MemberInfo;
import ee.ria.xroad.common.conf.serverconf.model.AccessRightType;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.LocalGroupType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceDescriptionType;
import ee.ria.xroad.common.conf.serverconf.model.ServiceType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.XRoadId;
import ee.ria.xroad.common.identifier.XRoadObjectType;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.Arrays;
import org.niis.xroad.restapi.dto.AccessRightHolderDto;
import org.niis.xroad.restapi.exceptions.BadRequestException;
import org.niis.xroad.restapi.exceptions.Error;
import org.niis.xroad.restapi.exceptions.NotFoundException;
import org.niis.xroad.restapi.repository.ClientRepository;
import org.niis.xroad.restapi.repository.LocalGroupRepository;
import org.niis.xroad.restapi.repository.ServiceDescriptionRepository;
import org.niis.xroad.restapi.util.FormatUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * service class for handling services
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("denyAll")
public class ServiceService {

    public static final String ERROR_SERVICE_NOT_FOUND = "services.service_not_found";
    public static final String ERROR_ACCESSRIGHT_NOT_FOUND = "services.accessright_not_found";

    private static final String HTTPS = "https";

    private final ClientRepository clientRepository;
    private final ClientService clientService;
    private final LocalGroupRepository localGroupRepository;
    private final ServiceDescriptionRepository serviceDescriptionRepository;
    private final GlobalConfService globalConfService;

    @Autowired
    public ServiceService(ClientRepository clientRepository, ClientService clientService,
            LocalGroupRepository localGroupRepository, ServiceDescriptionRepository serviceDescriptionRepository,
            GlobalConfService globalConfService) {
        this.clientRepository = clientRepository;
        this.clientService = clientService;
        this.localGroupRepository = localGroupRepository;
        this.serviceDescriptionRepository = serviceDescriptionRepository;
        this.globalConfService = globalConfService;
    }

    /**
     * get ServiceType by ClientId and service code that includes service version
     * see {@link FormatUtils#getServiceFullName(ServiceType)}
     * @param clientId
     * @param fullServiceCode
     * @return
     */
    @PreAuthorize("hasAuthority('VIEW_CLIENT_SERVICES')")
    public ServiceType getService(ClientId clientId, String fullServiceCode) {
        ClientType client = clientRepository.getClient(clientId);
        if (client == null) {
            throw new NotFoundException("Client " + clientId.toShortString() + " not found",
                    new Error(ClientService.CLIENT_NOT_FOUND_ERROR_CODE));
        }
        return getServiceFromClient(client, fullServiceCode);
    }

    /**
     * @param client
     * @param fullServiceCode
     * @return {@link ServiceType}
     */
    @PreAuthorize("hasAuthority('VIEW_CLIENT_SERVICES')")
    public ServiceType getServiceFromClient(ClientType client, String fullServiceCode) {
        Optional<ServiceType> foundService = client.getServiceDescription()
                .stream()
                .map(ServiceDescriptionType::getService)
                .flatMap(List::stream)
                .filter(serviceType -> FormatUtils.getServiceFullName(serviceType).equals(fullServiceCode))
                .findFirst();
        return foundService.orElseThrow(() -> new NotFoundException("Service " + fullServiceCode + " not found",
                new Error(ERROR_SERVICE_NOT_FOUND)));
    }

    /**
     * update a Service. clientId and fullServiceCode identify the updated service.
     * @param clientId clientId of the client associated with the service
     * @param fullServiceCode service code that includes service version
     * see {@link FormatUtils#getServiceFullName(ServiceType)}
     * @param url
     * @param urlAll
     * @param timeout
     * @param timeoutAll
     * @param sslAuth
     * @param sslAuthAll
     * @return ServiceType
     */
    @PreAuthorize("hasAuthority('EDIT_SERVICE_PARAMS')")
    public ServiceType updateService(ClientId clientId, String fullServiceCode,
            String url, boolean urlAll, Integer timeout, boolean timeoutAll,
            boolean sslAuth, boolean sslAuthAll) {
        if (!FormatUtils.isValidUrl(url)) {
            throw new BadRequestException("URL is not valid: " + url);
        }

        ServiceType serviceType = getService(clientId, fullServiceCode);

        if (serviceType == null) {
            throw new NotFoundException("Service " + fullServiceCode + " not found");
        }

        ServiceDescriptionType serviceDescriptionType = serviceType.getServiceDescription();

        serviceDescriptionType.getService().forEach(service -> {
            boolean serviceMatch = service == serviceType;
            if (urlAll || serviceMatch) {
                service.setUrl(url);
            }
            if (timeoutAll || serviceMatch) {
                service.setTimeout(timeout);
            }
            if (sslAuthAll || serviceMatch) {
                if (service.getUrl().startsWith(HTTPS)) {
                    service.setSslAuthentication(sslAuth);
                } else {
                    service.setSslAuthentication(null);
                }
            }
        });

        serviceDescriptionRepository.saveOrUpdate(serviceDescriptionType);

        return serviceType;
    }

    private AccessRightHolderDto accessRightTypeToDto(AccessRightType accessRightType,
            Map<String, LocalGroupType> localGroupMap) {
        AccessRightHolderDto accessRightHolderDto = new AccessRightHolderDto();
        XRoadId subjectId = accessRightType.getSubjectId();
        accessRightHolderDto.setRightsGiven(
                FormatUtils.fromDateToOffsetDateTime(accessRightType.getRightsGiven()));
        accessRightHolderDto.setSubjectId(subjectId);
        if (subjectId.getObjectType() == XRoadObjectType.LOCALGROUP) {
            LocalGroupId localGroupId = (LocalGroupId) subjectId;
            LocalGroupType localGroupType = localGroupMap.get(localGroupId.getGroupCode());
            accessRightHolderDto.setGroupId(localGroupType.getId().toString());
            accessRightHolderDto.setGroupCode(localGroupType.getGroupCode());
            accessRightHolderDto.setGroupDescription(localGroupType.getDescription());
        }
        return accessRightHolderDto;
    }

    /**
     * Get access right holders by Service
     * @param clientId
     * @param fullServiceCode
     * @return
     */
    @PreAuthorize("hasAuthority('VIEW_SERVICE_ACL')")
    public List<AccessRightHolderDto> getAccessRightHoldersByService(ClientId clientId, String fullServiceCode) {
        ClientType clientType = clientRepository.getClient(clientId);
        if (clientType == null) {
            throw new NotFoundException("Client " + clientId.toShortString() + " not found",
                    new Error(ClientService.CLIENT_NOT_FOUND_ERROR_CODE));
        }

        ServiceType serviceType = getServiceFromClient(clientType, fullServiceCode);

        List<AccessRightHolderDto> accessRightHolderDtos = new ArrayList<>();

        Map<String, LocalGroupType> localGroupMap = new HashMap<>();

        clientType.getLocalGroup().forEach(localGroupType -> localGroupMap.put(localGroupType.getGroupCode(),
                localGroupType));

        clientType.getAcl().forEach(accessRightType -> {
            if (accessRightType.getEndpoint().getServiceCode().equals(serviceType.getServiceCode())) {
                AccessRightHolderDto accessRightHolderDto = accessRightTypeToDto(accessRightType, localGroupMap);
                accessRightHolderDtos.add(accessRightHolderDto);
            }
        });

        return accessRightHolderDtos;
    }

    /**
     * Remove AccessRights from a Service
     * @param clientId
     * @param fullServiceCode
     * @param subjectIds
     */
    @PreAuthorize("hasAuthority('EDIT_SERVICE_ACL')")
    public void deleteServiceAccessRights(ClientId clientId, String fullServiceCode, Set<XRoadId> subjectIds) {
        ClientType clientType = clientRepository.getClient(clientId);
        if (clientType == null) {
            throw new NotFoundException("Client " + clientId.toShortString() + " not found",
                    new Error(ClientService.CLIENT_NOT_FOUND_ERROR_CODE));
        }

        ServiceType serviceType = getServiceFromClient(clientType, fullServiceCode);

        List<AccessRightType> accessRightsToBeRemoved = clientType.getAcl()
                .stream()
                .filter(accessRightType -> accessRightType.getEndpoint().getServiceCode()
                        .equals(serviceType.getServiceCode()) && subjectIds.contains(accessRightType.getSubjectId()))
                .collect(Collectors.toList());

        List<XRoadId> subjectsToBeRemoved = accessRightsToBeRemoved
                .stream()
                .map(AccessRightType::getSubjectId)
                .collect(Collectors.toList());

        if (!subjectsToBeRemoved.containsAll(subjectIds)) {
            throw new BadRequestException(new Error(ERROR_ACCESSRIGHT_NOT_FOUND));
        }

        clientType.getAcl().removeAll(accessRightsToBeRemoved);

        clientRepository.saveOrUpdate(clientType);
    }

    /**
     * Remove AccessRights from a Service
     * @param clientId
     * @param fullServiceCode
     * @param subjectIds
     * @param localGroupIds
     */
    @PreAuthorize("hasAuthority('EDIT_SERVICE_ACL')")
    public void deleteServiceAccessRights(ClientId clientId, String fullServiceCode, Set<XRoadId> subjectIds,
            Set<Long> localGroupIds) {
        Set<XRoadId> localGroups = localGroupIds
                .stream()
                .map(groupId -> {
                    LocalGroupType localGroup = localGroupRepository.getLocalGroup(groupId); // no need to batch
                    if (localGroup == null) {
                        throw new NotFoundException("LocalGroup with id " + groupId + " not found");
                    }
                    return LocalGroupId.create(localGroup.getGroupCode());
                })
                .collect(Collectors.toSet());

        subjectIds.addAll(localGroups);
        deleteServiceAccessRights(clientId, fullServiceCode, subjectIds);
    }

    /**
     * Find access right holders by
     * @param clientId
     * @param memberNameGroupDescription
     * @param subjectType
     * @param instance
     * @param memberClass
     * @param memberGroupCode
     * @param subsystemCode
     * @return AccessRightHolderDto list
     */
    @PreAuthorize("hasAuthority('VIEW_CLIENT_ACL_SUBJECTS')")
    public List<AccessRightHolderDto> findAccessRightHolders(ClientId clientId, String memberNameGroupDescription,
            XRoadObjectType subjectType, String instance, String memberClass, String memberGroupCode,
            String subsystemCode) {
        List<AccessRightHolderDto> dtos = new ArrayList<>();

        // get client
        // will throw a checked exception
        ClientType client = clientRepository.getClient(clientId);

        // GlobalConf::getGlobalMembers (only subsystems) - leaves unregistered local clients out
        List<MemberInfo> clients = globalConfService.getGlobalMembers();
        clients.forEach(memberInfo -> {
            AccessRightHolderDto accessRightHolderDto = new AccessRightHolderDto();
            accessRightHolderDto.setSubjectId(memberInfo.getId());
            accessRightHolderDto.setMemberName(memberInfo.getName());
            dtos.add(accessRightHolderDto);
        });

        // GlobalConf::getGlobalGroups
        String[] globalGroupInstances = new String[] {};
        if (instance != null) {
            globalGroupInstances = Arrays.append(globalGroupInstances, instance);
        } else {
            globalGroupInstances = globalConfService.getInstanceIdentifiers().toArray(globalGroupInstances);
        }
        List<GlobalGroupInfo> globalGroupInfos = globalConfService.getGlobalGroups(globalGroupInstances);
        globalGroupInfos.forEach(globalGroupInfo -> {
            AccessRightHolderDto accessRightHolderDto = new AccessRightHolderDto();
            accessRightHolderDto.setSubjectId(globalGroupInfo.getId());
            accessRightHolderDto.setGroupDescription(globalGroupInfo.getDescription());
            dtos.add(accessRightHolderDto);
        });

        // client.getLocalGroups
        List<LocalGroupType> localGroups = client.getLocalGroup();
        localGroups.forEach(localGroup -> {
            AccessRightHolderDto accessRightHolderDto = new AccessRightHolderDto();
            accessRightHolderDto.setSubjectId(LocalGroupId.create(localGroup.getGroupCode()));
            accessRightHolderDto.setGroupDescription(localGroup.getDescription());
            dtos.add(accessRightHolderDto);
        });

        Predicate<AccessRightHolderDto> matchingSearchTerms = buildSubjectSearchPredicate(subjectType,
                memberNameGroupDescription, instance, memberClass, memberGroupCode, subsystemCode);

        return dtos.stream()
                .filter(matchingSearchTerms)
                .collect(Collectors.toList());
    }

    private Predicate<AccessRightHolderDto> buildSubjectSearchPredicate(XRoadObjectType subjectType,
            String memberNameGroupDescription, String instance, String memberClass, String memberGroupCode,
            String subsystemCode) {
        Predicate<AccessRightHolderDto> searchPredicate = accessRightHolderDto -> true;
        if (subjectType != null) {
            searchPredicate = searchPredicate.and(dto -> dto.getSubjectId().getObjectType() == subjectType);
        }
        if (!StringUtils.isEmpty(memberNameGroupDescription)) {
            searchPredicate = searchPredicate.and(dto -> {
                String memberName = dto.getMemberName();
                String groupDescription = dto.getGroupDescription();
                boolean isMatch = (memberName != null && memberName.toLowerCase().contains(
                        memberNameGroupDescription.toLowerCase()))
                        || (groupDescription != null && groupDescription.toLowerCase().contains(
                        memberNameGroupDescription.toLowerCase()));
                return isMatch;
            });
        }
        if (!StringUtils.isEmpty(instance)) {
            searchPredicate = searchPredicate.and(dto -> {
                XRoadId xRoadId = dto.getSubjectId();
                // LocalGroups do not have explicit X-Road instances -> always return
                if (xRoadId instanceof LocalGroupId) {
                    return true;
                } else {
                    return dto.getSubjectId().getXRoadInstance().toLowerCase().contains(instance.toLowerCase());
                }
            });
        }
        if (!StringUtils.isEmpty(memberClass)) {
            searchPredicate = searchPredicate.and(dto -> {
                XRoadId xRoadId = dto.getSubjectId();
                if (xRoadId instanceof ClientId) {
                    return ((ClientId) xRoadId).getMemberClass().toLowerCase().contains(memberClass.toLowerCase());
                } else {
                    return false;
                }
            });
        }
        if (!StringUtils.isEmpty(subsystemCode)) {
            searchPredicate = searchPredicate.and(dto -> {
                XRoadId xRoadId = dto.getSubjectId();
                if (xRoadId instanceof ClientId) {
                    String clientSubsystemCode = ((ClientId) xRoadId).getSubsystemCode();
                    return clientSubsystemCode != null && clientSubsystemCode
                            .toLowerCase()
                            .contains(subsystemCode.toLowerCase());
                } else {
                    return false;
                }
            });
        }
        if (!StringUtils.isEmpty(memberGroupCode)) {
            searchPredicate = searchPredicate.and(dto -> {
                XRoadId xRoadId = dto.getSubjectId();
                if (xRoadId instanceof ClientId) {
                    return ((ClientId) xRoadId).getMemberCode()
                            .toLowerCase()
                            .contains(memberGroupCode.toLowerCase());
                } else if (xRoadId instanceof GlobalGroupId) {
                    return ((GlobalGroupId) xRoadId).getGroupCode()
                            .toLowerCase()
                            .contains(memberGroupCode.toLowerCase());
                } else if (xRoadId instanceof LocalGroupId) {
                    return ((LocalGroupId) xRoadId).getGroupCode()
                            .toLowerCase()
                            .contains(memberGroupCode.toLowerCase());
                } else {
                    return false;
                }
            });
        }
        searchPredicate = searchPredicate.and(dto -> dto.getSubjectId().getObjectType() != XRoadObjectType.MEMBER);
        return searchPredicate;
    }
}
