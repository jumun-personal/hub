package com.jumunhasyeo.hub.hub.domain.entity;

import com.jumunhasyeo.common.BaseEntity;
import com.jumunhasyeo.common.exception.BusinessException;
import com.jumunhasyeo.common.exception.ErrorCode;
import com.jumunhasyeo.hub.hub.domain.vo.Address;
import com.jumunhasyeo.hub.hub.domain.vo.Coordinate;
import io.micrometer.common.util.StringUtils;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.domain.Persistable;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.jumunhasyeo.hub.hub.domain.entity.HubType.BRANCH;
import static com.jumunhasyeo.hub.hub.domain.entity.HubType.CENTER;

@Entity
@Table(
        name = "p_hub",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_hub_name_deleted",
                columnNames = {"name", "is_deleted"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Hub extends BaseEntity implements Persistable<UUID> {

    @Id
    @Column(name = "hub_id", columnDefinition = "UUID")
    private UUID hubId;

    @Column(name = "name", nullable = false, length = 20)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "hub_type")
    private HubType hubType;

    @Embedded
    private Address address;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private HubStatus status = HubStatus.PENDING;

    /**
     * this Hub의 CENTER
     */
    @OneToMany(mappedBy = "centerHub", cascade = CascadeType.PERSIST)
    @Builder.Default
    private Set<HubRelation> centerHubRelations = new HashSet<>();

    /**
     * this Hub의 CENTER
     */
    @OneToMany(mappedBy = "branchHub", cascade = CascadeType.PERSIST)
    @Builder.Default
    private Set<HubRelation> branchHubRelations = new HashSet<>();

    private Hub(String name, Address address, HubType hubType) {
        this.hubId = UUID.randomUUID();
        this.name = name;
        this.address = address;
        this.hubType = hubType;
        this.status = HubStatus.PENDING;
        this.centerHubRelations = new HashSet<>();
        this.branchHubRelations = new HashSet<>();
    }

    public static Hub createBranchHub(String name, Address address) {
        return of(name, address, BRANCH);
    }

    public static Hub of(String name, Address address, HubType hubType) {
        validate(name, address);
        return new Hub(name, address, hubType);
    }

    public void update(String name, Address address) {
        validate(name, address);
        this.name = name;
        this.address = address;
    }

    public void activate() {
        this.status = HubStatus.COMPLETE;
    }

    public void markFailed(Long userId) {
        this.status = HubStatus.FAILED;
        markDeleted(userId);
    }

    public void delete(Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.MUST_NOT_NULL, "userId");
        }

        // CENTER Hub는 BRANCH가 없을 때만 삭제 가능
        if (isCenterHub()) {
            Set<Hub> branchHubs = getBranchHubs();
            if (!branchHubs.isEmpty()) {
                throw new BusinessException(ErrorCode.CANNOT_DELETE_CENTER_HUB_WITH_BRANCHES);
            }
        }

        // BRANCH Hub는 검증 없이 삭제 가능
        markDeleted(userId);
    }

    public HubRelation addCenterHub(Hub centerHub) {
        if(!isBranchHub()){
            throw new BusinessException(ErrorCode.BRANCH_HUB_ONLY_CAN_ADD_CENTER_HUB);
        }

        // 이미 등록된 센터인지 확인
        Optional<HubRelation> existing = findCenterHub(centerHub);
        if(existing.isPresent()){
            return existing.get();
        }

        // 새로운 관계 생성
        HubRelation relation = HubRelation.of(this, centerHub);

        // 양방향 연관관계 설정
        this.branchHubRelations.add(relation);      // branch인 나의 관계에 추가
        centerHub.addBranchHubInternal(relation);   // center의 관계에 추가

        return relation;
    }

    public boolean isCenterHub() {
        return CENTER.equals(hubType);
    }

    public boolean isBranchHub() {
        return BRANCH.equals(hubType);
    }

    public boolean isActive() {
        return HubStatus.COMPLETE.equals(status);
    }

    public Set<Hub> getBranchHubs(){
        return centerHubRelations.stream()
                .map(HubRelation::getBranchHub)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<Hub> getCenterHubs(){
        return branchHubRelations.stream()
                .map(HubRelation::getCenterHub)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Coordinate getCoordinate() {
        return this.address.getCoordinate();
    }

    @PrePersist
    void assignId() {
        if (hubId == null) {
            hubId = UUID.randomUUID();
        }
    }

    @Override
    public UUID getId() {
        return hubId;
    }

    @Override
    public boolean isNew() {
        return getCreatedAt() == null;
    }

    private static void validate(String name, Address address) {
        if (StringUtils.isEmpty(name) || address == null) {
            throw new BusinessException(ErrorCode.CREATE_VALIDATE_EXCEPTION);
        }
    }

    private void addBranchHubInternal(HubRelation relation) {
        this.centerHubRelations.add(relation);
    }

    private Optional<HubRelation> findCenterHub(Hub centerHub) {
        return branchHubRelations.stream()
                .filter(r -> r.getCenterHub().equals(centerHub))
                .findFirst();
    }
}
