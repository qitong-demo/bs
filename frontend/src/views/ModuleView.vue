<template>
  <section class="module-content">
    <header class="module-heading">
      <div>
        <div class="module-kicker">新就业群体服务 / {{ view }}</div>
        <h1>{{ currentConfig.title }}</h1>
        <p>{{ currentConfig.description }}</p>
      </div>
      <div class="module-actions">
        <el-button @click="$emit('back-home')">
          <el-icon><HomeFilled /></el-icon>
          返回首页
        </el-button>
        <el-button v-if="currentConfig.action" type="primary" :loading="actionLoading" @click="handlePrimary">
          <el-icon><Plus /></el-icon>
          {{ currentConfig.action }}
        </el-button>
      </div>
    </header>

    <el-alert v-if="dataNote" class="module-note" type="info" :closable="false" show-icon>
      {{ dataNote }}
      <template v-if="cityScopeNote">{{ cityScopeNote }}</template>
    </el-alert>

    <!-- ------------------------------------------------------ 驿站查询 -->
    <template v-if="view === '驿站查询'">
      <div class="module-toolbar">
        <el-input
          v-model="moduleKeyword"
          clearable
          placeholder="搜索驿站名称、地址或服务项目"
          class="toolbar-search"
          @keyup.enter="searchStations"
          @clear="searchStations"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="selectedArea" placeholder="全部区域" class="toolbar-select" @change="searchStations">
          <el-option label="全部区域" value="全部区域" />
          <el-option
            v-for="item in stationAreaOptions"
            :key="item.area"
            :label="`${item.area}（${item.count}）`"
            :value="item.area"
          />
        </el-select>
        <el-select v-model="serviceType" placeholder="全部服务" class="toolbar-select" @change="searchStations">
          <el-option label="全部服务" value="全部服务" />
          <el-option v-for="service in stationServiceOptions" :key="service" :label="service" :value="service" />
        </el-select>
        <el-tooltip
          content="按驿站是否已启用过滤（服务端字段）。是否正在营业请看每行右侧的状态标签，它按当前时间实时判断。"
          placement="top"
        >
          <el-checkbox v-model="openOnly" @change="searchStations">只显示已启用</el-checkbox>
        </el-tooltip>
        <el-button :loading="loading.stations" @click="loadStationRows">刷新</el-button>
      </div>
      <div class="station-directory" v-loading="loading.stations">
        <div class="directory-list">
          <button
            v-for="(station, index) in stationRows"
            :key="station.id"
            class="directory-row"
            :class="{ selected: selectedDirectoryStation?.id === station.id }"
            @click="selectStation(station)"
          >
            <span class="directory-rank">{{ stationRank(index) }}</span>
            <span class="directory-info">
              <strong>{{ station.name }}</strong>
              <small>{{ station.address || "地址待核验" }} · {{ station.openTime || "开放时间待核验" }}</small>
              <span class="tags">
                <el-tag size="small" effect="light">{{ station.stationType || "未分类" }}</el-tag>
                <el-tag v-for="tag in station.tags.slice(0, 4)" :key="tag" size="small" type="success" effect="light">{{ tag }}</el-tag>
                <el-tag v-if="station.tags.length > 4" size="small" type="info" effect="plain">+{{ station.tags.length - 4 }}</el-tag>
              </span>
            </span>
            <el-tag :type="stationOpenMeta(station).tag" effect="light">
              {{ stationOpenMeta(station).label }}
            </el-tag>
            <el-icon><ArrowRight /></el-icon>
          </button>
          <el-empty v-if="!loading.stations && stationRows.length === 0" description="没有符合条件的驿站" />
        </div>
        <aside class="directory-detail panel">
          <div class="detail-cover">
            <StationMap :city="currentCity" :stations="selectedStationRows" height="100%" />
          </div>
          <div class="detail-body">
            <div class="detail-title">
              <div>
                <h2>{{ selectedDirectoryStation?.name || "请选择驿站" }}</h2>
                <p>{{ selectedDirectoryStation?.area || "区域待核验" }} · {{ selectedDirectoryStation?.stationType || "未分类" }}</p>
              </div>
              <el-tag :type="stationOpenMeta(selectedDirectoryStation).tag">
                {{ stationOpenMeta(selectedDirectoryStation).label }}
              </el-tag>
            </div>
            <dl class="detail-list">
              <div><dt>地址</dt><dd>{{ selectedDirectoryStation?.address || "地址待核验" }}</dd></div>
              <div>
                <dt>营业信息</dt>
                <dd>
                  <div>开放时间：{{ selectedDirectoryStation?.openTime || "开放时间待核验" }}</div>
                  <div>联系电话：{{ selectedDirectoryStation?.contactPhone || "联系方式待核验" }}</div>
                  <el-tag v-if="businessSourceLabel" size="small" :type="businessSourceTag" effect="plain">
                    来源：{{ businessSourceLabel }}
                  </el-tag>
                </dd>
              </div>
              <div v-if="hasCoordinate">
                <dt>坐标</dt>
                <dd>
                  {{ coordinateText }}
                  <el-tag size="small" type="info" effect="plain">高德地图派生</el-tag>
                </dd>
              </div>
              <div v-if="selectedDirectoryStation">
                <dt>导航</dt>
                <dd>
                  <a :href="amapSearchUrl(selectedDirectoryStation)" target="_blank" rel="noopener">在高德地图中搜索该驿站</a>
                </dd>
              </div>
            </dl>

            <!-- 服务设施：高德 POI 没有这个结构化字段，只能来自公开来源文本，因此弱化展示并写明出处 -->
            <div class="detail-services detail-services-muted">
              <span>
                服务设施
                <small class="muted-hint">来自公开来源文本，高德地图未提供该字段</small>
              </span>
              <div v-if="serviceTags.length" class="tags">
                <el-tag v-for="tag in serviceTags" :key="tag" type="success" effect="light">{{ tag }}</el-tag>
              </div>
              <p v-else class="muted-hint">来源未逐条给出服务设施</p>
            </div>
            <div class="station-source">
              <div class="station-source-head">
                <span>数据来源</span>
                <el-tag :type="sourceStatusTag(sourceInfo.dataStatus)" effect="light">
                  {{ sourceStatusLabel(sourceInfo.dataStatus) }}
                </el-tag>
              </div>
              <dl class="detail-list station-source-list">
                <div><dt>驿站类型</dt><dd>{{ selectedDirectoryStation?.stationType || "未分类" }}</dd></div>
                <div><dt>来源名称</dt><dd>{{ sourceInfo.sourceName || "—" }}</dd></div>
                <div v-if="sourceInfo.sourceUrl">
                  <dt>来源链接</dt>
                  <dd><a :href="sourceInfo.sourceUrl" target="_blank" rel="noopener">{{ sourceInfo.sourceUrl }}</a></dd>
                </div>
              </dl>
              <p class="station-source-date">
                来源发布日期 {{ sourceInfo.sourcePublishedAt || "—" }} · 核对日期 {{ sourceInfo.verifiedAt || "—" }}
              </p>
              <el-alert v-if="sourceInfo.dataNote" class="station-source-note" type="warning" :closable="false" show-icon>
                {{ sourceInfo.dataNote }}
              </el-alert>
            </div>
            <div class="detail-actions">
              <el-button type="primary" class="full" @click="openReservation(selectedDirectoryStation)">预约服务</el-button>
              <el-button class="full" @click="toggleFavorite(selectedDirectoryStation)">
                {{ isFavorite(selectedDirectoryStation?.id) ? "取消收藏" : "收藏驿站" }}
              </el-button>
            </div>
          </div>
        </aside>
      </div>
      <div class="pagination-row station-pagination">
        <el-pagination
          layout="prev, pager, next, total"
          :page-size="stationQuery.size"
          :current-page="stationQuery.current"
          :total="stationTotal"
          @current-change="onStationPageChange"
        />
      </div>
    </template>

    <!-- ------------------------------------------------------ 服务预约 -->
    <template v-else-if="view === '服务预约'">
      <div class="module-layout two-columns">
        <section class="panel form-panel">
          <div class="sub-heading">
            <h2>提前联系驿站</h2>
            <span>说明你的情况，由驿站确认是否可行</span>
          </div>
          <p class="form-hint">
            驿站服务预约用于<b>有事需要提前和驿站沟通</b>——例如借用场地、团体到访、咨询政策、
            需要特定帮助等。饮水、休息这类随时可用的小事可以直接前往，不必预约。
          </p>
          <el-form label-position="top" :model="reservationForm">
            <el-form-item label="服务驿站">
              <el-select v-model="reservationForm.stationId" class="full" filterable @change="onReservationStationChange">
                <el-option v-for="station in stationOptions" :key="station.id" :label="station.name" :value="station.id" />
              </el-select>
              <p v-if="reservationOpenTime" class="field-hint">该驿站营业时间：{{ reservationOpenTime }}</p>
            </el-form-item>
            <el-form-item label="想提前沟通什么">
              <el-input
                v-model="reservationForm.subject"
                type="textarea"
                :rows="3"
                maxlength="100"
                show-word-limit
                placeholder="例如：我们 10 人想借用驿站场地开一次安全培训，能否安排？"
              />
            </el-form-item>
            <el-form-item label="联系电话（便于驿站回复确认）">
              <el-input v-model="reservationForm.contactPhone" maxlength="30" placeholder="选填，方便驿站打电话确认" />
            </el-form-item>
            <el-form-item label="希望联系的日期">
              <el-date-picker v-model="reservationForm.date" type="date" value-format="YYYY-MM-DD" class="full" placeholder="选择日期" />
            </el-form-item>
            <el-form-item label="方便联系的时段">
              <el-radio-group v-model="reservationForm.slot">
                <el-radio-button v-for="slot in reservationSlots" :key="slot" :label="slot" />
              </el-radio-group>
              <p v-if="!reservationSlots.length" class="field-hint">该驿站暂无可选时段，请先选择驿站。</p>
            </el-form-item>
            <el-button type="primary" class="full" :loading="loading.submit" @click="submitReservation">
              提交联系申请
            </el-button>
          </el-form>
        </section>
        <section class="panel">
          <div class="sub-heading">
            <h2>预约记录</h2>
            <el-tag type="info" effect="plain">共 {{ myAppointments.length }} 条</el-tag>
          </div>
          <div class="record-list" v-loading="loading.appointments">
            <div v-for="item in myAppointments" :key="item.id" class="record-row">
              <div>
                <strong>{{ item.serviceName }}</strong>
                <span>{{ item.stationName }}</span>
              </div>
              <div class="record-meta">
                <span>{{ item.appointDate }} {{ item.appointTime }}</span>
                <el-tag :type="appointmentTagType(item.status)" effect="plain">{{ item.statusLabel }}</el-tag>
                <el-button
                  v-if="item.status === 'PENDING_STATION_CONFIRMATION'"
                  link
                  type="danger"
                  @click="cancelMyAppointment(item)"
                >取消</el-button>
              </div>
            </div>
          <el-empty v-if="!loading.appointments && myAppointments.length === 0" :image-size="64" description="暂无预约记录" />
          </div>
        </section>
      </div>
    </template>

    <!-- ------------------------------------------------------ 诉求与反馈 -->
    <template v-else-if="view === '诉求与反馈'">
      <div class="module-layout two-columns">
        <section class="panel form-panel">
          <div class="sub-heading">
            <h2>提交诉求或反馈</h2>
            <span>问题、建议、感谢都可以提交，提交后在「我的诉求」跟踪进度</span>
          </div>

          <!-- 分类决定表单措辞与子类型：反映问题用「问题」、表扬感谢用「想说的话」 -->
          <div class="category-picker">
            <button
              v-for="option in appealCategories"
              :key="option.value"
              type="button"
              class="category-option"
              :class="{ active: appealForm.category === option.value }"
              @click="selectAppealCategory(option.value)"
            >
              <el-icon><component :is="option.icon" /></el-icon>
              <strong>{{ option.label }}</strong>
              <small>{{ option.hint }}</small>
            </button>
          </div>

          <el-form label-position="top" :model="appealForm">
            <el-form-item :label="appealCategoryMeta.titleLabel">
              <el-input v-model="appealForm.title" :placeholder="appealCategoryMeta.titlePlaceholder" />
            </el-form-item>
            <el-form-item :label="appealCategoryMeta.typeLabel">
              <el-select v-model="appealForm.type" class="full">
                <el-option v-for="type in appealCategoryMeta.types" :key="type" :label="type" :value="type" />
              </el-select>
            </el-form-item>
            <el-form-item label="反馈给谁">
              <el-radio-group v-model="appealForm.receiverTarget">
                <el-radio-button label="STATION">驿站（由驿站管理员受理）</el-radio-button>
                <el-radio-button label="PLATFORM">平台管理员</el-radio-button>
              </el-radio-group>
              <p class="field-hint">{{ receiverTargetHint }}</p>
            </el-form-item>
            <el-form-item v-if="appealForm.receiverTarget === 'STATION'" label="反馈给哪个驿站">
              <el-select
                v-model="appealForm.stationId"
                class="full"
                filterable
                clearable
                placeholder="选择驿站，诉求会直达该驿站"
                @change="onAppealStationChange"
              >
                <el-option v-for="station in appealStationOptions" :key="station.id" :label="station.name" :value="station.id" />
              </el-select>
              <p class="field-hint">选择后区域会以驿站档案为准，避免区域填错导致驿站端看不到。</p>
            </el-form-item>
            <el-form-item label="所属区域">
              <el-select v-model="appealForm.area" class="full">
                <el-option v-for="area in areaOptions" :key="area" :label="area" :value="area" />
              </el-select>
            </el-form-item>
            <el-form-item :label="appealCategoryMeta.contentLabel">
              <el-input
                v-model="appealForm.content"
                type="textarea"
                :rows="5"
                :placeholder="appealCategoryMeta.contentPlaceholder"
              />
            </el-form-item>
            <el-checkbox v-model="appealForm.anonymous">匿名提交</el-checkbox>
            <el-button type="primary" class="full form-submit" :loading="loading.submit" @click="submitAppeal">
              {{ appealCategoryMeta.submitLabel }}
            </el-button>
          </el-form>
        </section>
        <section class="panel progress-panel">
          <div class="sub-heading">
            <h2>我的诉求进度</h2>
            <el-button link type="primary" @click="$emit('change-view', '我的诉求')">查看全部</el-button>
          </div>
          <div class="timeline" v-loading="loading.appeals">
            <div v-for="item in myAppeals.slice(0, 4)" :key="item.id" class="timeline-item">
              <span class="timeline-dot" :class="`timeline-dot--${appealDotTone(item.status)}`"></span>
              <div>
                <strong>{{ item.title }}</strong>
                <p>
                  <span class="category-chip" :class="`category-chip--${appealCategoryTone(item.category)}`">
                    {{ item.categoryLabel || "反映问题" }}
                  </span>
                  {{ item.statusLabel }} · {{ item.createTime || "时间待记录" }}
                </p>
              </div>
            </div>
            <el-empty v-if="!loading.appeals && myAppeals.length === 0" :image-size="64" description="还没有提交过诉求或反馈" />
          </div>
          <div class="help-callout">
            <el-icon><InfoFilled /></el-icon>
            <span>反映问题与提出建议由对应受理方跟进；表扬感谢会转达给驿站与工作人员。</span>
          </div>
        </section>
      </div>
    </template>

    <!-- ------------------------------------------------------ 我的诉求 -->
    <template v-else-if="view === '我的诉求'">
      <section class="panel">
        <div class="sub-heading">
          <h2>诉求记录</h2>
          <el-radio-group v-model="appealFilter" size="small">
            <el-radio-button label="全部" />
            <el-radio-button label="待受理" />
            <el-radio-button label="处理中" />
            <el-radio-button label="已办结" />
          </el-radio-group>
        </div>
        <el-table :data="filteredAppealRows" class="clean-table" v-loading="loading.appeals" empty-text="暂无诉求记录">
          <el-table-column prop="title" label="诉求标题" min-width="280" />
          <el-table-column prop="type" label="类型" width="110" />
          <el-table-column prop="area" label="区域" width="100" />
          <el-table-column prop="handlerName" label="处理人" width="110" />
          <el-table-column prop="createTime" label="提交时间" width="170" />
          <el-table-column label="当前状态" width="110">
            <template #default="{ row }">
              <el-tag :type="appealTagType(row.status)" effect="plain">{{ row.statusLabel }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="170" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openAppealDetail(row)">查看进度</el-button>
              <el-tag v-if="row.evaluated" size="small" type="info" effect="plain">已评价</el-tag>
              <el-button v-else-if="row.status === 'RESOLVED'" link type="success" @click="openEvaluationDialog(row)">评价</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>
      <section class="panel detail-progress" v-if="selectedAppeal">
        <div class="sub-heading">
          <h2>处理流程</h2>
          <span>当前选中：{{ selectedAppeal.title }}</span>
        </div>
        <el-steps :active="appealStep(selectedAppeal)" finish-status="success" align-center>
          <el-step :title="appealStepTitles[0]" :description="selectedAppeal.createTime || '已提交'" />
          <el-step :title="appealStepTitles[1]" :description="stepTime('ASSIGN')" />
          <el-step :title="appealStepTitles[2]" :description="stepTime('HANDLE')" />
          <el-step :title="appealStepTitles[3]" :description="selectedAppeal.finishTime || appealStepTitles[3]" />
        </el-steps>

        <!-- 回复才是这条反馈的「结果」：驿站/管理员亲口说的话，单独突出显示而不是埋进时间线 -->
        <div v-if="appealReply" class="appeal-reply">
          <div class="appeal-reply-head">
            <el-icon><ChatDotRound /></el-icon>
            <strong>{{ appealReply.from }}</strong>
            <time v-if="appealReply.time">{{ appealReply.time }}</time>
          </div>
          <p class="appeal-reply-body">{{ appealReply.text }}</p>
        </div>
        <div v-else class="appeal-reply appeal-reply--pending">
          <div class="appeal-reply-head">
            <el-icon><Clock /></el-icon>
            <strong>{{ selectedAppeal.receiverName || "受理方" }}尚未回复</strong>
          </div>
          <p class="appeal-reply-body">{{ selectedAppeal.statusLabel }}</p>
        </div>

        <el-timeline class="appeal-timeline">
          <el-timeline-item
            v-for="record in selectedAppeal.records || []"
            :key="record.id || record.createTime + record.action"
            :timestamp="record.createTime"
            placement="top"
          >
            <strong>{{ record.actionLabel }}</strong>
            <p>{{ record.content || "无补充说明" }}<span v-if="record.handlerName"> · {{ record.handlerName }}</span></p>
          </el-timeline-item>
        </el-timeline>
      </section>
    </template>

    <!-- ------------------------------------------------------ 活动报名 -->
    <template v-else-if="view === '活动报名'">
      <div class="activity-grid" v-loading="loading.activities">
        <article v-for="activity in activityItems" :key="activity.id" class="activity-item">
          <!-- 色条按来源分两种，不用装饰色区分卡片序号：紫色/蓝色/橙色与活动含义无关。
               站内自办用品牌主色，采集活动用中性信息色，扫一眼就知道哪类不能站内报名。 -->
          <div
            class="activity-accent"
            :class="isExternalActivity(activity) ? 'activity-accent--external' : 'activity-accent--station'"
          ></div>
          <div class="activity-body">
            <div class="activity-top">
              <div class="activity-tags">
                <!-- 来源：驿站 / 平台 / 第三方机构（采集活动带来源等级） -->
                <el-tag :type="isExternalActivity(activity) ? 'warning' : 'info'" effect="light">
                  {{ activitySourceLabel(activity) }}
                </el-tag>
                <!-- 来源等级只对采集活动有值（政府群团 / 官方媒体）；驿站自办的 sourceLevel 为空，
                     sourceLevelLabel 却是「驿站自办」，与来源标签重复，所以按 sourceLevel 判断。 -->
                <el-tag v-if="activity.sourceLevel && activity.sourceLevelLabel" type="info" effect="plain" size="small">
                  {{ activity.sourceLevelLabel }}
                </el-tag>
                <!-- 活动分类：后端可能为空串，空则不出标签 -->
                <el-tag v-if="activity.category" effect="plain" size="small">{{ activity.category }}</el-tag>
                <!-- 采集活动可能来自其他城市或全国：标明地点口径，别让人以为是本地活动 -->
                <el-tag :type="activityCityTagType(activity)" effect="plain" size="small">
                  {{ activityCityName(activity) }}
                </el-tag>
              </div>
              <el-tag :type="activityStatusTagType(activity)" effect="plain" size="small">
                {{ activityStatusText(activity) }}
              </el-tag>
            </div>
            <h2>{{ activity.title }}</h2>
            <!-- 主办方：采集活动有 publisher；驿站自办为空，则退回显示承办驿站。
                 两者都为空时整行不渲染，不出现「主办：」后面跟空白的标签。 -->
            <p v-if="activity.publisher || activity.stationName" class="activity-publisher">
              <el-icon><OfficeBuilding /></el-icon>
              {{ activity.publisher ? `主办：${activity.publisher}` : `承办驿站：${activity.stationName}` }}
            </p>
            <p><el-icon><Location /></el-icon>{{ activityPlaceText(activity) }}</p>
            <p><el-icon><Calendar /></el-icon>{{ activityTimeText(activity) }}</p>
            <!-- 摘要优先：采集活动的正文是新闻正文，摘要是为列表读一屏写的 -->
            <p class="activity-summary">{{ activity.summary || activity.content || "主办方暂未提供活动说明" }}</p>
            <div class="activity-footer">
              <span v-if="!canSignupInSite(activity)" class="activity-hint">{{ activitySignupHint(activity) }}</span>
              <span v-else>已报名 {{ activity.signedCount }} / {{ activity.maxCount }}</span>
              <div class="activity-actions">
                <el-button plain @click="openActivityDetail(activity)">查看详情</el-button>
                <!-- 采集活动不接受站内报名：后端报名接口会直接 400，报名与审核都在主办方，
                     所以这里只保留「前往原文」跳转，不出现站内报名按钮。 -->
                <el-button
                  v-if="!canSignupInSite(activity)"
                  type="primary"
                  :disabled="!externalUrlText(activity)"
                  @click="openExternalActivity(activity)"
                >
                  {{ externalUrlText(activity) ? "前往原文" : "暂无原文链接" }}
                  <el-icon v-if="externalUrlText(activity)" class="el-icon--right"><TopRight /></el-icon>
                </el-button>
                <el-button
                  v-else
                  :disabled="activity.remaining <= 0 || signedActivityIds.includes(activity.id)"
                  type="primary"
                  @click="openSignup(activity)"
                >{{ signedActivityIds.includes(activity.id) ? "已报名" : "立即报名" }}</el-button>
              </div>
            </div>
          </div>
        </article>
        <el-empty v-if="!loading.activities && activityItems.length === 0" :image-size="64" description="暂无可报名活动" />
      </div>
      <!-- 活动一共 40 条（38 条采集 + 2 条驿站自办），一页 12 条翻不完就看不到后面，
           所以列表补一个分页，控件与「政策资讯」保持同一套。 -->
      <div class="pagination-row">
        <el-pagination
          layout="prev, pager, next"
          :total="activityTotal"
          :page-size="activityQuery.size"
          :current-page="activityQuery.current"
          @current-change="onActivityPageChange"
        />
      </div>

      <!-- 报名表单：姓名与电话必填，承办方要靠这些联系报名人 -->
      <el-dialog v-model="signupVisible" title="填写报名信息" width="460px">
        <p class="dialog-hint">
          驿站会根据这些信息联系你，确认时间与安排。请留常用的电话。
        </p>
        <el-form label-position="top">
          <el-form-item label="姓名" required>
            <el-input v-model="signupForm.contactName" maxlength="50" placeholder="请填写真实姓名，方便驿站称呼与核对" />
          </el-form-item>
          <el-form-item label="联系电话" required>
            <el-input v-model="signupForm.phone" maxlength="30" placeholder="例如：13800002088" />
          </el-form-item>
          <el-form-item label="备注（选填）">
            <el-input
              v-model="signupForm.remark"
              type="textarea"
              :rows="3"
              maxlength="255"
              show-word-limit
              placeholder="方便的时间、同行人数等，驿站会参考"
            />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="signupVisible = false">取消</el-button>
          <el-button type="primary" :loading="signupSubmitting" @click="submitSignup">提交报名</el-button>
        </template>
      </el-dialog>

      <!-- 我的报名：报名之后能看到承办方是否联系过、有没有通过、说了什么 -->
      <el-dialog v-model="mySignupsVisible" title="我的报名" width="600px">
        <div v-loading="mySignupsLoading">
          <div v-if="mySignupsList.length" class="my-signup-list">
            <div v-for="item in mySignupsList" :key="item.id" class="my-signup-item">
              <div class="my-signup-head">
                <strong>{{ item.activityTitle || "（活动已下架）" }}</strong>
                <el-tag :type="signupTagType(item.status)" effect="plain" size="small">{{ item.statusLabel }}</el-tag>
              </div>
              <p class="my-signup-meta">报名时间：{{ item.createTime }}</p>
              <!-- 承办方的反馈是这条报名真正要等的东西，单独显示 -->
              <p v-if="item.reviewNote" class="my-signup-review">
                <span>{{ item.reviewerName || "承办方" }}的回复：</span>{{ item.reviewNote }}
              </p>
              <p v-else class="my-signup-muted">承办方还没有回复，可留意电话或消息</p>
              <el-button
                v-if="['SIGNED_UP', 'CONTACTED'].includes(item.status)"
                size="small"
                type="danger"
                plain
                @click="cancelMySignup(item)"
              >取消报名</el-button>
            </div>
          </div>
          <el-empty v-else-if="!mySignupsLoading" :image-size="64" description="还没有报名记录" />
        </div>
      </el-dialog>

      <!-- 活动详情：站内自办与采集活动共用一套，采集活动额外给原文入口与免责说明 -->
      <el-dialog v-model="activityDetailVisible" :title="activeActivity?.title" width="660px">
        <div v-if="activeActivity" class="activity-detail">
          <div class="activity-tags">
            <el-tag :type="isExternalActivity(activeActivity) ? 'warning' : 'info'" effect="light">
              {{ activitySourceLabel(activeActivity) }}
            </el-tag>
            <el-tag v-if="activeActivity.sourceLevel && activeActivity.sourceLevelLabel" type="info" effect="plain" size="small">
              {{ activeActivity.sourceLevelLabel }}
            </el-tag>
            <el-tag v-if="activeActivity.category" effect="plain" size="small">{{ activeActivity.category }}</el-tag>
            <el-tag :type="activityCityTagType(activeActivity)" effect="plain" size="small">
              {{ activityCityName(activeActivity) }}
            </el-tag>
            <el-tag :type="activityStatusTagType(activeActivity)" effect="plain" size="small">
              {{ activityStatusText(activeActivity) }}
            </el-tag>
          </div>
          <dl class="activity-detail-meta">
            <div v-if="activeActivity.publisher">
              <dt>主办方</dt>
              <dd>{{ activeActivity.publisher }}</dd>
            </div>
            <div v-if="activeActivity.stationName">
              <dt>承办驿站</dt>
              <dd>{{ activeActivity.stationName }}</dd>
            </div>
            <div>
              <dt>活动时间</dt>
              <dd>{{ activityTimeText(activeActivity) }}</dd>
            </div>
            <div>
              <dt>活动地点</dt>
              <dd>{{ activityPlaceText(activeActivity) }}</dd>
            </div>
            <!-- 名额只对站内报名有意义：采集活动本站不放号 -->
            <div v-if="canSignupInSite(activeActivity)">
              <dt>报名情况</dt>
              <dd>已报名 {{ activeActivity.signedCount }} / {{ activeActivity.maxCount }}，剩余 {{ activeActivity.remaining }}</dd>
            </div>
          </dl>
          <p v-if="activeActivity.summary" class="activity-detail-summary">{{ activeActivity.summary }}</p>
          <p v-if="activityBodyText(activeActivity)" class="activity-detail-content">
            {{ activityBodyText(activeActivity) }}
          </p>
          <!-- 采集活动：说清报名与审核不在本站，避免用户以为站内能报上 -->
          <el-alert
            v-if="!canSignupInSite(activeActivity)"
            class="activity-detail-note"
            type="info"
            :closable="false"
            show-icon
          >
            该活动由主办方负责报名与审核，本站仅提供信息。
            <template v-if="!externalUrlText(activeActivity)">
              该条记录未提供原文链接，暂无法跳转，可联系平台管理员核对。
            </template>
          </el-alert>
        </div>
        <template #footer>
          <el-button @click="activityDetailVisible = false">关闭</el-button>
          <el-button
            v-if="!canSignupInSite(activeActivity)"
            type="primary"
            :disabled="!externalUrlText(activeActivity)"
            @click="openExternalActivity(activeActivity)"
          >前往原文</el-button>
          <el-button
            v-else
            type="primary"
            :disabled="!activeActivity || activeActivity.remaining <= 0 || signedActivityIds.includes(activeActivity.id)"
            @click="signupFromDetail(activeActivity)"
          >{{ signedActivityIds.includes(activeActivity?.id) ? "已报名" : "立即报名" }}</el-button>
        </template>
      </el-dialog>
    </template>

    <!-- ------------------------------------------------------ 政策资讯 -->
    <template v-else-if="view === '政策资讯'">
      <section class="panel">
        <div class="sub-heading">
          <h2>政策与资讯</h2>
          <div class="news-filters">
            <!-- 来源筛选：官方政策与平台政策分开看（骑手可以只看美团/京东这类平台规则） -->
            <el-radio-group v-model="newsSourceFilter" size="small" @change="resetNewsAndLoad">
              <el-radio-button label="全部" />
              <el-radio-button label="官方" />
              <el-radio-button label="平台" />
            </el-radio-group>
            <!-- 按平台筛选：骑手可以只看自己关心的平台动态（美团/京东/滴滴…） -->
            <el-select
              v-model="newsPlatformFilter"
              size="small"
              clearable
              placeholder="全部平台"
              class="news-platform-select"
              @change="resetNewsAndLoad"
            >
              <el-option v-for="name in NEWS_PLATFORMS" :key="name" :label="name" :value="name" />
            </el-select>
            <!-- 分类筛选用资讯的真实分类（权益保障/职业伤害/…），
                 不再写死「政策/通知/动态」——那套词与资讯分类对不上，筛了基本命中不到。 -->
            <el-select
              v-model="newsFilter"
              size="small"
              clearable
              placeholder="全部分类"
              class="news-category-select"
              @change="resetNewsAndLoad"
            >
              <el-option v-for="name in NEWS_CATEGORIES" :key="name" :label="name" :value="name" />
            </el-select>
          </div>
        </div>
        <div class="news-directory" v-loading="loading.news">
          <button v-for="item in newsItems" :key="item.source + item.id" class="news-directory-row" @click="openNewsDetail(item)">
            <el-tag :type="newsType(item)" effect="light">{{ newsLabel(item) }}</el-tag>
            <!-- 来源：官方 / 平台（平台资讯会带上平台名，如「美团」）。
                 第三方来源在同一格加提示——出处与可信度放一起，
                 也避免多占一列把整行挤到换行。 -->
            <span class="news-source-cell">
              <el-tag :type="item.sourceType === 'PLATFORM' ? 'warning' : 'info'" effect="plain" size="small">
                {{ item.platform || item.sourceTypeLabel || "官方" }}
              </el-tag>
              <el-tag v-if="item.sourceLevel === 'SECONDARY'" type="danger" effect="plain" size="small">第三方</el-tag>
            </span>
            <strong>{{ item.title }}</strong>
            <!-- 城市相关的排在前面并标出城市；不限城市的显示「全国」 -->
            <el-tag v-if="item.cityCode" type="success" effect="plain" size="small">{{ cityNameOf(item.cityCode) }}</el-tag>
            <el-tag v-else type="info" effect="plain" size="small">全国</el-tag>
            <span>{{ item.publishedAt || item.date }}</span>
            <el-icon><ArrowRight /></el-icon>
          </button>
          <el-empty v-if="!loading.news && newsItems.length === 0" :image-size="64" description="暂无资讯" />
        </div>
        <div class="pagination-row">
          <el-pagination
            layout="prev, pager, next"
            :total="newsTotal"
            :page-size="newsQuery.size"
            :current-page="newsQuery.current"
            @current-change="onNewsPageChange"
          />
        </div>
      </section>
    </template>

    <!-- ------------------------------------------------------ 服务评价 -->
    <template v-else-if="view === '服务评价'">
      <div class="module-layout two-columns">
        <section class="panel form-panel">
          <div class="sub-heading"><h2>评价驿站服务</h2><span>你的反馈会帮助驿站持续改进</span></div>
          <el-form label-position="top" :model="evaluationForm">
            <el-form-item label="评价驿站">
              <el-select v-model="evaluationForm.stationId" class="full" filterable @change="loadStationEvaluations">
                <el-option v-for="station in stationOptions" :key="station.id" :label="station.name" :value="station.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="综合评分"><el-rate v-model="evaluationForm.score" show-text /></el-form-item>
            <el-form-item label="服务评分"><el-rate v-model="evaluationForm.serviceScore" /></el-form-item>
            <el-form-item label="环境评分"><el-rate v-model="evaluationForm.environmentScore" /></el-form-item>
            <el-form-item label="设施评分"><el-rate v-model="evaluationForm.facilityScore" /></el-form-item>
            <el-form-item label="服务评价">
              <el-input v-model="evaluationForm.content" type="textarea" :rows="4" placeholder="说说你的体验" />
            </el-form-item>
            <el-button type="primary" class="full" :loading="loading.submit" @click="submitEvaluation">提交评价</el-button>
          </el-form>
        </section>
        <section class="panel">
          <div class="sub-heading">
            <h2>历史评价</h2>
            <el-tag type="success" effect="plain">
              {{ evaluationSummary.count }} 条 · 平均 {{ evaluationSummary.averageScore }} 分
            </el-tag>
          </div>
          <div class="evaluation-list" v-loading="loading.evaluations">
            <div v-for="item in stationEvaluations" :key="item.id" class="evaluation-row">
              <div class="evaluation-head">
                <strong>{{ item.userName || "匿名用户" }}</strong>
                <span>{{ item.createTime }}</span>
              </div>
              <el-rate :model-value="item.score" disabled />
              <p>{{ item.content || "未填写评价内容" }}</p>
            </div>
            <el-empty v-if="!loading.evaluations && stationEvaluations.length === 0" :image-size="64" description="该驿站还没有评价" />
          </div>
        </section>
      </div>
    </template>

    <!-- ------------------------------------------------------ 消息中心 -->
    <template v-else-if="view === '消息中心'">
      <section class="panel">
        <div class="sub-heading">
          <h2>
            全部消息
            <span v-if="messageStore.unreadCount" class="heading-badge">{{ messageStore.unreadCount }} 条未读</span>
          </h2>
          <el-button link type="primary" :disabled="!messageStore.unreadCount" @click="markMessagesRead">
            {{ messageStore.unreadCount ? "全部标为已读" : "已全部读完" }}
          </el-button>
        </div>
        <div class="message-list">
          <button
            v-for="item in messageStore.items"
            :key="item.key"
            class="message-row"
            :class="{ unread: !messageStore.isRead(item.key) }"
            @click="messageStore.markRead(item.key)"
          >
            <span class="message-icon" :class="`message-icon--${item.type}`"><el-icon><Bell /></el-icon></span>
            <div><strong>{{ item.title }}</strong><p>{{ item.detail }}</p></div>
            <time>{{ item.time }}</time>
            <span v-if="!messageStore.isRead(item.key)" class="unread-dot"></span>
          </button>
          <el-empty v-if="messageStore.items.length === 0" :image-size="64" description="暂无消息" />
          <div v-if="messageStore.hasMore" class="message-load-more">
            <el-button link type="primary" :loading="messageStore.loadingMore" @click="messageStore.loadMore()">
              加载更多
            </el-button>
          </div>
        </div>
      </section>
    </template>

    <!-- ------------------------------------------------------ 数据统计 -->
    <template v-else-if="view === '数据统计'">
      <div class="analytics-grid" v-loading="loading.stat">
        <section v-for="item in analyticsCards" :key="item.label" class="analytics-card">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
          <el-progress :percentage="item.percent" :color="item.color" :show-text="false" />
          <small>{{ item.note }}</small>
        </section>
      </div>
      <section class="panel service-ranking">
        <div class="sub-heading">
          <h2>驿站联系申请排行</h2>
          <span>{{ statOverview.dataScope || "数据口径加载中" }}</span>
        </div>
        <div v-for="(item, index) in rankingRows" :key="item.name" class="ranking-row">
          <b>{{ index + 1 }}</b>
          <span>{{ item.name }}</span>
          <el-progress :percentage="item.percent" :show-text="false" />
          <strong>{{ item.count }} 次</strong>
        </div>
        <el-empty v-if="rankingRows.length === 0" :image-size="64" description="还没有预约数据" />
      </section>
      <section class="panel">
        <div class="sub-heading">
          <h2>月度趋势</h2>
          <el-button link type="primary" @click="exportMyData">导出我的数据</el-button>
        </div>
        <el-table :data="trendRows" class="clean-table" empty-text="暂无趋势数据">
          <el-table-column prop="month" label="月份" width="120" />
          <el-table-column prop="appointments" label="预约量" width="120" />
          <el-table-column prop="appeals" label="诉求量" width="120" />
          <el-table-column prop="signups" label="活动报名" width="120" />
          <el-table-column label="诉求办结率" min-width="160">
            <template #default="{ row }">{{ row.finishRate }}%</template>
          </el-table-column>
        </el-table>
      </section>
    </template>

    <!-- ------------------------------------------------------ 我的收藏 -->
    <template v-else-if="view === '我的收藏'">
      <section class="panel">
        <div class="sub-heading"><h2>我的收藏</h2><span>收藏的驿站会在这里集中展示</span></div>
        <div class="favorite-list" v-loading="loading.stations">
          <button v-for="station in favoriteStations" :key="station.id" class="favorite-row" @click="openReservation(station)">
            <el-icon><StarFilled /></el-icon>
            <div>
              <strong>{{ station.name }}</strong>
              <span>{{ station.area }} · {{ station.tags.slice(0, 3).join(" / ") || "服务项目待补充" }}</span>
            </div>
            <el-icon><ArrowRight /></el-icon>
          </button>
          <el-empty v-if="favoriteStations.length === 0" :image-size="64" description="还没有收藏驿站，可在驿站查询页收藏" />
        </div>
      </section>
    </template>

    <!-- ------------------------------------------------------ 个人中心 -->
    <template v-else>
      <section class="panel profile-panel">
        <div class="sub-heading"><h2>个人资料</h2><span>维护你的基本信息和常用服务偏好</span></div>
        <el-form label-position="top" :model="profileForm" class="profile-form" v-loading="loading.profile">
          <el-form-item label="账号"><el-input :model-value="profileForm.username" readonly /></el-form-item>
          <el-form-item label="姓名"><el-input v-model="profileForm.realName" /></el-form-item>
          <el-form-item label="手机号"><el-input v-model="profileForm.phone" /></el-form-item>
          <el-form-item label="职业类型">
            <el-select v-model="profileForm.jobType" class="full" placeholder="请选择你从事的工作">
              <el-option v-for="item in JOB_TYPES" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="所属区域">
            <el-select v-model="profileForm.area" class="full">
              <el-option v-for="area in areaOptions" :key="area" :label="area" :value="area" />
            </el-select>
          </el-form-item>
          <el-form-item label="角色">
            <el-tag effect="plain">{{ profileForm.roleName || userStore.role }}</el-tag>
          </el-form-item>
          <el-button type="primary" :loading="loading.submit" @click="saveProfile">保存修改</el-button>
        </el-form>
      </section>

      <!-- 账号安全：改账号 ID、改密码、绑定邮箱，三项都需邮箱验证码 -->
      <AccountSecurityPanel />
    </template>

    <!-- ------------------------------------------------------ 预约弹窗 -->
    <el-dialog v-model="reservationVisible" title="提前联系驿站" width="520px">
      <p class="form-hint">
        说明你的情况，驿站管理员会确认是否可行。饮水、休息这类随时可用的小事可直接前往。
      </p>
      <el-form label-position="top" :model="reservationForm">
        <el-form-item label="驿站">
          <el-input :model-value="reservationForm.stationName" readonly />
          <p v-if="reservationOpenTime" class="field-hint">营业时间：{{ reservationOpenTime }}</p>
        </el-form-item>
        <el-form-item label="想提前沟通什么">
          <el-input
            v-model="reservationForm.subject"
            type="textarea"
            :rows="3"
            maxlength="100"
            show-word-limit
            placeholder="例如：想确认能否借用场地开一次安全培训"
          />
        </el-form-item>
        <el-form-item label="联系电话（便于驿站回复确认）">
          <el-input v-model="reservationForm.contactPhone" maxlength="30" placeholder="选填" />
        </el-form-item>
        <el-form-item label="希望联系的日期">
          <el-date-picker v-model="reservationForm.date" type="date" value-format="YYYY-MM-DD" class="full" placeholder="选择日期" />
        </el-form-item>
        <el-form-item label="方便联系的时段">
          <el-radio-group v-model="reservationForm.slot">
            <el-radio-button v-for="slot in reservationSlots" :key="slot" :label="slot" />
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reservationVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading.submit" @click="submitReservation">提交联系申请</el-button>
      </template>
    </el-dialog>

    <!-- ------------------------------------------------------ 资讯详情 -->
    <el-dialog v-model="newsVisible" :title="activeNews?.title" width="680px">
      <p class="news-meta">{{ newsLabel(activeNews || {}) }} · {{ activeNews?.date }}</p>
      <p class="news-content">{{ activeNews?.content || "该条内容暂无正文，请以官方来源为准。" }}</p>
    </el-dialog>

    <!-- ------------------------------------------------------ 诉求评价 -->
    <el-dialog v-model="appealEvaluationVisible" title="评价诉求处理" width="480px">
      <el-form label-position="top">
        <el-form-item label="办结评分"><el-rate v-model="appealEvaluationForm.score" show-text /></el-form-item>
        <el-form-item label="评价内容">
          <el-input v-model="appealEvaluationForm.content" type="textarea" :rows="3" placeholder="处理是否及时、结果是否满意" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="appealEvaluationVisible = false">取消</el-button>
        <el-button type="primary" :loading="loading.submit" @click="submitAppealEvaluation">提交评价</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import StationMap from "../components/StationMap.vue";
import { errorMessage } from "../api/http";
import { currentUser, updateProfile } from "../api/auth";
import { getStationAreas, getStationPage, listStationServices } from "../api/station";
import {
  cancelAppointment,
  cancelSignup,
  createAppeal,
  createAppointment,
  evaluateAppeal,
  getAppealDetail,
  getMyAppeals,
  getMyAppointments,
  getMySignups,
  signupActivity
} from "../api/workflow";
import { getActivityPage, getNewsPage, getNoticePage } from "../api/content";
import {
  createStationEvaluation,
  getMyEvaluations,
  getStationEvaluationPage,
  getStationEvaluationSummary
} from "../api/evaluation";
import { getMonthlyTrend, getServiceRanking, getStatOverview } from "../api/stat";
import { useUserStore } from "../stores/user";
import { useMessageStore } from "../stores/message";
import { buildSlots, openStateMeta } from "../utils/business-hours";
import {
  activitySignupHint,
  activitySourceLabel,
  externalUrlText,
  isExternalActivity,
  canSignupInSite
} from "../utils/activity-source";
import AccountSecurityPanel from "./AccountSecurityPanel.vue";
import { JOB_TYPES } from "../utils/profile-options";
import { NEWS_CATEGORIES, NEWS_PLATFORMS } from "../constants/news-platforms";

const props = defineProps({
  view: { type: String, required: true },
  /** 首页点「提前联系」时带过来的驿站 id：切到服务预约并预选它。 */
  prefillStationId: { type: [Number, String], default: null }
});

const emit = defineEmits(["back-home", "change-view", "prefill-consumed"]);

const userStore = useUserStore();
/** 消息与已读状态由共享 store 持有，顶栏未读角标读的是同一份数据。 */
const messageStore = useMessageStore();

/**
 * 实时营业状态：按当前时间与驿站营业时间判断「营业中 / 已打烊 / 营业时间待核验」。
 * 每分钟走一次，让状态随时间自行变化，而不是停在页面打开的那一刻。
 */
const now = ref(new Date());
let nowTimer = null;
onMounted(() => {
  nowTimer = setInterval(() => {
    now.value = new Date();
  }, 60000);
});
onUnmounted(() => {
  if (nowTimer) clearInterval(nowTimer);
});
function stationOpenMeta(station) {
  return openStateMeta(station?.openTime, now.value, station?.status);
}

const loading = reactive({
  stations: false,
  appointments: false,
  appeals: false,
  activities: false,
  news: false,
  evaluations: false,
  stat: false,
  profile: false,
  submit: false,
  action: false
});
const actionLoading = computed(() => loading.action);
const dataNote = ref("");

/** 顶部说明里的城市口径：模块说明若已提到当前城市就不再重复追加，避免出现两遍城市名。 */
const cityScopeNote = computed(() => {
  const cityName = userStore.cityName;
  if (!cityName || (dataNote.value || "").includes(cityName)) return "";
  return `当前城市：${cityName}。`;
});

const moduleKeyword = ref("");
const selectedArea = ref("全部区域");
const serviceType = ref("全部服务");
const openOnly = ref(true);
const selectedDirectoryStation = ref(null);

/** 驿站查询的分页状态：列表改为服务端分页，size 不超过后端上限 50。 */
const stationQuery = reactive({ current: 1, size: 12 });
/** 当前页驿站（后端返回的 records）。 */
const stationRows = ref([]);
/** 后端返回的符合条件的总条数，用于分页控件。 */
const stationTotal = ref(0);
/** 区域下拉数据：[{ area, count }]，来自 /api/station/areas。 */
const stationAreaOptions = ref([]);
/** 服务类型候选：选中值作为 service 参数交给后端按服务项目名称模糊匹配。 */
const stationServiceOptions = ["休息区", "充电", "饮水", "热饭", "空调", "免费茶水", "应急药箱", "换电柜"];
/**
 * 「服务预约」「服务评价」「我的收藏」共用的驿站下拉选项。
 * 只取当前城市的前 50 条（后端 size 上限）用于下拉选择，不代表该城市全部点位。
 */
const stationOptions = ref([]);

const reservationVisible = ref(false);
const reservationForm = reactive({
  stationId: null,
  stationName: "",
  /** 用户自填的沟通事项（服务项目不再从固定列表里选）。 */
  subject: "",
  /** 便于驿站回电确认，可不填。 */
  contactPhone: "",
  date: "",
  slot: ""
});
/** 当前所选驿站的营业时间，用来生成合法时段并给出提示。 */
const reservationOpenTime = ref("");
/** 可选时段按营业时间生成，避免出现后端必然拒绝的选项。 */
const reservationSlots = computed(() => buildSlots(reservationOpenTime.value));
const myAppointments = ref([]);

const appealForm = reactive({
  category: "REQUEST",
  /** 反馈对象：STATION 驿站 / PLATFORM 平台管理员（开题报告要求两种都支持）。 */
  receiverTarget: "STATION",
  /** 定向驿站：选择后诉求按 station_id 精准路由，不再依赖自由文本区域。 */
  stationId: null,
  title: "",
  type: "驿站服务",
  area: "",
  content: "",
  anonymous: false
});

/** 选中区域后只列该区域的驿站；未选区域时列当前城市全部可选驿站。 */
const appealStationOptions = computed(() => {
  if (!appealForm.area) return stationOptions.value;
  return stationOptions.value.filter((station) => station.area === appealForm.area);
});

function onAppealStationChange(stationId) {
  const station = stationOptions.value.find((item) => item.id === stationId);
  if (station?.area) {
    appealForm.area = station.area;
  }
}

/** 反馈对象的说明文字，随分类变化——表扬感谢给驿站最合适。 */
const receiverTargetHint = computed(() => {
  if (appealForm.category === "PRAISE") {
    return "表扬感谢建议反馈给驿站，会直接转达给当时帮助你的工作人员。";
  }
  if (appealForm.receiverTarget === "STATION") {
    return "由该驿站的驿站管理员受理；涉及驿站设施与服务的问题走这里。";
  }
  return "由平台管理员受理；涉及跨驿站、政策或权益保障的问题走这里。";
});

/**
 * 诉求与反馈的三类内容。
 * 分开的理由：这个模块不只承载「提出问题」，也承载骑手与驿站之间的正向互助——
 * 例如「驿站放了免费雨衣，骑手用过后自己买了补上」「骑手受伤，驿站帮忙包扎，骑手来道谢」。
 * 这类内容不是问题也不是投诉，表单措辞与状态文案都应该不一样。
 */
const appealCategories = [
  {
    value: "REQUEST",
    label: "反映问题",
    hint: "需要解决的事",
    icon: "Warning",
    titleLabel: "问题标题",
    titlePlaceholder: "例如：希望增加夜间热饭服务",
    typeLabel: "问题类型",
    types: ["驿站服务", "权益保障", "设施建议", "其他问题"],
    contentLabel: "具体内容",
    contentPlaceholder: "请描述遇到的问题、发生地点和希望得到的帮助",
    submitLabel: "提交诉求"
  },
  {
    value: "PRAISE",
    label: "表扬感谢",
    hint: "想对帮助说声谢谢",
    icon: "Star",
    titleLabel: "想感谢谁 · 一句话概括",
    titlePlaceholder: "例如：感谢湖滨驿站帮我处理伤口",
    typeLabel: "感谢类型",
    types: ["热心帮助", "互帮互助", "拾金不昧", "暖心服务"],
    contentLabel: "想说的话",
    contentPlaceholder: "说说当时发生了什么。例如：驿站设了免费雨衣，我用掉一件，后来自己买了几件补回去，谢谢驿站的信任。",
    submitLabel: "提交感谢"
  },
  {
    value: "SUGGESTION",
    label: "提出建议",
    hint: "希望改进的地方",
    icon: "Promotion",
    titleLabel: "建议标题",
    titlePlaceholder: "例如：建议延长周末的开放时间",
    typeLabel: "建议类型",
    types: ["服务时间", "设施配置", "流程优化", "其他建议"],
    contentLabel: "具体建议",
    contentPlaceholder: "请说明现状、你的想法，以及希望达到的效果",
    submitLabel: "提交建议"
  }
];

/** 当前分类对应的措辞与子类型。 */
const appealCategoryMeta = computed(
  () => appealCategories.find((item) => item.value === appealForm.category) || appealCategories[0]
);

/** 列表里的分类标签配色：表扬用暖色以示正向，问题用中性灰，建议用主色。 */
function appealCategoryTone(category) {
  if (category === "PRAISE") return "praise";
  if (category === "SUGGESTION") return "suggestion";
  return "request";
}

/**
 * 流程步骤标题随分类变化，最后一步落在「回复」上——
 * 这条反馈的结果不是「已办结」这个动作，而是驿站/管理员实际说的话。
 */
const appealStepTitles = computed(() => {
  const category = selectedAppeal.value?.category;
  if (category === "PRAISE") return ["心意已送达", "已转达驿站", "驿站查看中", "驿站的回应"];
  if (category === "SUGGESTION") return ["建议已提交", "已转达相关同事", "评估中", "处理意见"];
  return ["已提交", "已受理", "处理中", "处理结果"];
});

/**
 * 驿站/管理员的回复：取处理记录里最后一条带内容的记录。
 * 这是整条反馈真正有意义的部分，因此单独渲染而不是只放在时间线里。
 */
const appealReply = computed(() => {
  const appeal = selectedAppeal.value;
  if (!appeal) return null;
  const records = (appeal.records || []).filter((item) => String(item.content || "").trim());
  if (!records.length) return null;
  // 优先取办结/关闭这类终结动作的回复，没有就取最后一条
  const final = [...records].reverse().find((item) => ["RESOLVED", "CLOSED"].includes(item.action)) || records[records.length - 1];
  const responder = final.handlerName || appeal.receiverName || "受理方";
  return {
    from: `${responder}的回复`,
    text: final.content,
    time: final.createTime || appeal.finishTime || ""
  };
});

/** 切换分类时同步默认子类型，避免出现「表扬感谢 + 权益保障」这种不搭的组合。 */
function selectAppealCategory(value) {
  appealForm.category = value;
  const meta = appealCategories.find((item) => item.value === value) || appealCategories[0];
  appealForm.type = meta.types[0];
  // 表扬感谢默认给驿站：要谢的是具体帮助过你的那个驿站
  appealForm.receiverTarget = value === "PRAISE" ? "STATION" : "PLATFORM";
}
const appealFilter = ref("全部");
const myAppeals = ref([]);
const selectedAppeal = ref(null);
const appealEvaluationVisible = ref(false);
const appealEvaluationForm = reactive({ appealId: null, score: 5, content: "" });

const activityItems = ref([]);
const signedActivityIds = ref([]);
/** 活动列表分页：后端一页最多 100 条，这里按 12 条一页翻。 */
const activityQuery = reactive({ current: 1, size: 12 });
const activityTotal = ref(0);
/** 报名表单：姓名与电话必填，承办方要靠这些联系报名人。 */
const signupVisible = ref(false);
const signupSubmitting = ref(false);
const signupTarget = ref(null);
const signupForm = reactive({ contactName: "", phone: "", remark: "" });
/** 活动详情弹窗：数据全部来自列表记录（后端详情字段与列表一致，不再多请求一次）。 */
const activityDetailVisible = ref(false);
const activeActivity = ref(null);

// 分类筛选初始为空：下拉是 clearable 的，选项只有真实分类（权益保障/职业伤害/…），
// 没有「全部」这一项。原先初始值写 "全部"，它是真值，会被当成分类名发给后端，
// 后端按 category='全部' 过滤 → 一条都返回不了（政策资讯页空白）。
// 来源筛选是单选组、确有「全部」选项，故那边保留 "全部" 并在请求前转换。
const newsFilter = ref("");
const newsItems = ref([]);
/** 来源筛选：全部 / 官方 / 平台。 */
const newsSourceFilter = ref("全部");
/** 平台筛选：空表示不限，可选值由 scripts/generate-news-seed.mjs 生成。 */
const newsPlatformFilter = ref("");
const newsTotal = ref(0);
const newsQuery = reactive({ current: 1, size: 8 });
const newsVisible = ref(false);
const activeNews = ref(null);

const evaluationForm = reactive({
  stationId: null,
  score: 5,
  serviceScore: 5,
  environmentScore: 5,
  facilityScore: 5,
  content: ""
});
const stationEvaluations = ref([]);
const evaluationSummary = ref({ count: 0, averageScore: 0 });

const statOverview = ref({});
const rankingRows = ref([]);
const trendRows = ref([]);

const favorites = ref(readFavorites());
/**
 * 预约、诉求、报名任一变化就重建消息列表。
 * 用 watch 而不是在若干加载函数里各调一次，避免漏掉某条加载路径导致角标数字不准。
 */
watch(
  [myAppointments, myAppeals, signedActivityIds],
  ([appointments, appeals, signupIds]) => {
    messageStore.sync({ appointments, appeals, signupIds });
  },
  { deep: false }
);

const profileForm = reactive({
  username: "",
  realName: "",
  phone: "",
  jobType: "",
  area: "",
  roleName: ""
});

const viewConfigs = {
  驿站查询: { title: "附近友好驿站", description: "按区域和服务类型快速找到适合你的服务点。", action: "刷新列表" },
  服务预约: { title: "服务预约", description: "提前预约充电、热饭、咨询和检修服务，减少现场等待。", action: "查看我的预约" },
  诉求与反馈: { title: "诉求与反馈", description: "反映问题、提出建议，或对驿站和工作人员的帮助说声谢谢，都会有人跟进。", action: "查看处理指南" },
  我的诉求: { title: "我的诉求", description: "查看每一条诉求的受理、分派、处理和评价进度。", action: "刷新进度" },
  活动报名: { title: "活动报名", description: "免费体检、法律讲座、技能培训等关爱活动持续更新。", action: "查看我的报名" },
  政策资讯: { title: "政策资讯", description: "及时了解劳动保障、职业培训和新就业群体相关政策。", action: "刷新资讯" },
  服务评价: { title: "服务评价", description: "留下真实体验，帮助友好驿站持续提升服务质量。", action: "查看我的评价" },
  消息中心: { title: "消息中心", description: "预约、诉求、活动和平台通知都会在这里提醒你。", action: "全部已读" },
  数据统计: { title: "数据统计", description: "了解平台服务使用情况和驿站服务热度。", action: "导出报表" },
  我的收藏: { title: "我的收藏", description: "集中管理你常用的驿站。", action: "管理收藏" },
  个人中心: { title: "个人中心", description: "完善个人信息，让平台推荐更适合你的服务。", action: "刷新资料" }
};

const currentConfig = computed(() => viewConfigs[props.view] || viewConfigs.个人中心);

/**
 * 区域候选：来自 /api/station/areas 的真实区域分布（按驿站数量倒序，已按当前城市过滤）。
 * 接口尚未返回或该城市暂无数据时给出中性兜底项，避免下拉框为空。
 */
const areaOptions = computed(() => {
  const areas = stationAreaOptions.value.map((item) => item.area).filter(Boolean);
  return areas.length ? areas : ["全部区域"];
});

/** 当前选中驿站的来源信息，用于详情面板的「数据来源」区块。 */
const sourceInfo = computed(() => selectedDirectoryStation.value?.source || {});

/**
 * 营业信息（开放时间 / 电话）的来源标签。
 * SOURCE = 公开来源文本；AMAP_POI = 高德地图同步的第三方数据；SOURCE+AMAP_POI = 两者混合。
 */
const businessSourceLabel = computed(() => {
  const source = sourceInfo.value.businessSource || "";
  if (source === "SOURCE") return "公开来源";
  if (source === "AMAP_POI") return "高德同步";
  if (source === "SOURCE+AMAP_POI") return "来源 + 高德";
  return "";
});
const businessSourceTag = computed(() => {
  const source = sourceInfo.value.businessSource || "";
  if (source === "SOURCE") return "success";
  if (source === "AMAP_POI") return "warning";
  return "info";
});
/** 有坐标时展示，并标注是高德派生。 */
const hasCoordinate = computed(() => {
  const station = selectedDirectoryStation.value;
  return Boolean(station && station.longitude != null && station.latitude != null);
});
const coordinateText = computed(() => {
  const station = selectedDirectoryStation.value;
  return station && station.longitude != null && station.latitude != null
    ? `${Number(station.longitude).toFixed(5)}, ${Number(station.latitude).toFixed(5)}`
    : "";
});
/** 服务设施来自公开来源文本，高德没有这个结构化字段，没有数据时给出中性说明。 */
const serviceTags = computed(() => selectedDirectoryStation.value?.tags || []);

/** 当前城市对象（含地图中心点），供驿站详情面板的 StationMap 定位。 */
const currentCity = computed(() => {
  const hit = userStore.cities.find((item) => item.cityCode === userStore.cityCode);
  return hit || { cityName: userStore.cityName, centerLng: null, centerLat: null };
});

/** 驿站详情地图只展示当前选中的这一个驿站；无坐标时组件会自行提示。 */
const selectedStationRows = computed(() => (selectedDirectoryStation.value ? [selectedDirectoryStation.value] : []));

/** 高德地图搜索外链：用「驿站名称 + 地址」作为关键词，无坐标时也能查看导航。 */
function amapSearchUrl(station) {
  const query = `${station?.name || ""} ${station?.address || ""}`.trim();
  return `https://www.amap.com/search?query=${encodeURIComponent(query)}`;
}

const favoriteStations = computed(() =>
  stationOptions.value.filter((item) => favorites.value.includes(item.id))
);

const filteredAppealRows = computed(() => {
  if (appealFilter.value === "全部") return myAppeals.value;
  return myAppeals.value.filter((item) => item.statusLabel === appealFilter.value);
});

const messageItems = computed(() => messageStore.items);

const analyticsCards = computed(() => {
  const overview = statOverview.value || {};
  const finishRate = Number(overview.finishRate || 0);
  const evaluationRate = Math.round((Number(overview.averageScore || 0) / 5) * 100);
  return [
    { label: "驿站点位（在库）", value: String(overview.stationCount ?? "—"), percent: Math.min(100, (overview.openStationCount || 0) * 10), color: "#18bf8f", note: `营业中 ${overview.openStationCount ?? 0} 个` },
    { label: "预约总量", value: String(overview.appointmentCount ?? "—"), percent: Math.min(100, (overview.appointmentCount || 0) * 5), color: "#2f80ed", note: `近 30 天 ${overview.monthlyAppointmentCount ?? 0} 条` },
    { label: "诉求总量", value: String(overview.appealCount ?? "—"), percent: Math.min(100, (overview.appealCount || 0) * 5), color: "#f5a623", note: `待处理 ${overview.pendingAppealCount ?? 0} 条` },
    { label: "诉求办结率", value: `${finishRate}%`, percent: Math.min(100, finishRate), color: "#009b84", note: `已办结 ${overview.resolvedAppealCount ?? 0} 条` },
    { label: "活动报名", value: String(overview.signupCount ?? "—"), percent: Math.min(100, (overview.signupCount || 0) * 5), color: "#18bf8f", note: `活动 ${overview.activityCount ?? 0} 场` },
    { label: "服务评价均分", value: `${overview.averageScore ?? 0} / 5`, percent: evaluationRate, color: "#2f80ed", note: `评价 ${overview.evaluationCount ?? 0} 条` }
  ];
});

// ------------------------------------------------------------------ 工具

function appointmentTagType(status) {
  return { CONFIRMED: "success", REJECTED: "danger", CANCELLED: "info", FINISHED: "success" }[status] || "primary";
}
function appealTagType(status) {
  return { RESOLVED: "success", CLOSED: "info", ASSIGNED: "warning", PROCESSING: "warning" }[status] || "primary";
}
function appealDotTone(status) {
  return status === "RESOLVED" || status === "CLOSED" ? "primary" : "warning";
}
/** 城市代码转城市名，用于资讯的城市标签。 */
function cityNameOf(code) {
  const hit = userStore.cities.find((item) => item.cityCode === code);
  return hit?.cityName || code;
}

function newsLabel(item) {
  if (!item) return "资讯";
  return item.source === "notice" ? item.noticeType || "通知" : item.category || "政策";
}
function newsType(item) {
  const label = newsLabel(item);
  return { 政策: "success", 通知: "primary", 动态: "warning" }[label] || "info";
}
/** 来源数据状态文案：VERIFIED / PARTIAL / 其他（含 MANUAL、UNKNOWN）。 */
function sourceStatusLabel(status) {
  return { VERIFIED: "来源可核对", PARTIAL: "部分字段为批次级信息" }[status] || "后台录入";
}
/** 来源数据状态标签色：可核对绿色、批次级橙色、其他灰色。 */
function sourceStatusTag(status) {
  return { VERIFIED: "success", PARTIAL: "warning" }[status] || "info";
}
function appealStep(appeal) {
  if (!appeal) return 0;
  if (appeal.status === "RESOLVED" || appeal.status === "CLOSED") return 4;
  if (appeal.status === "PROCESSING") return 3;
  if (appeal.status === "ASSIGNED") return 2;
  return 1;
}
function stepTime(action) {
  const records = selectedAppeal.value?.records || [];
  const hit = records.find((item) => item.action === action);
  return hit ? hit.createTime : "待处理";
}

function readFavorites() {
  try {
    return JSON.parse(localStorage.getItem("friendly_station_favorites") || "[]");
  } catch {
    return [];
  }
}
function isFavorite(id) {
  return Boolean(id) && favorites.value.includes(id);
}
function toggleFavorite(station) {
  if (!station?.id) return;
  if (isFavorite(station.id)) {
    favorites.value = favorites.value.filter((item) => item !== station.id);
    ElMessage.success("已取消收藏");
  } else {
    favorites.value = [...favorites.value, station.id];
    ElMessage.success("已收藏该驿站");
  }
  localStorage.setItem("friendly_station_favorites", JSON.stringify(favorites.value));
}
/** 全部标记已读：写回共享 store，顶栏角标随之清零。 */
function markMessagesRead() {
  if (!messageStore.unreadCount) return;
  messageStore.markAllRead();
  ElMessage.success("消息已全部标记为已读");
}

// ------------------------------------------------------------------ 驿站

/** 把后端 StationDto 映射为页面使用的字段，保留 source 供详情展示来源。 */
function mapStation(item) {
  return {
    id: item.id,
    cityCode: item.cityCode || "",
    name: item.stationName,
    stationType: item.stationType || "",
    area: item.area,
    address: item.address,
    contactPhone: item.contactPhone,
    openTime: item.openTime,
    longitude: item.longitude,
    latitude: item.latitude,
    tags: item.services || [],
    status: item.status,
    source: item.source || {}
  };
}

/** 列表序号按当前页码连续编号。 */
function stationRank(index) {
  return (stationQuery.current - 1) * stationQuery.size + index + 1;
}

/** 顶部说明中的点位总数使用后端返回的 total，并按当前所选城市表述。 */
function updateStationNote() {
  const cityLabel = userStore.cityName || "当前城市";
  dataNote.value = `驿站名录来自公开来源，可在详情中查看来源与核对日期；${cityLabel}共 ${stationTotal.value} 个点位（当前筛选结果），已停用的点位不提供现场服务。`;
}

/** 区域下拉的选项来自 /api/station/areas，只统计当前城市，避免混入其他城市的区县。 */
async function loadStationAreas() {
  try {
    stationAreaOptions.value = (await getStationAreas({ cityCode: userStore.cityCode || undefined })) || [];
  } catch (error) {
    ElMessage.error(errorMessage(error, "区域统计加载失败"));
    stationAreaOptions.value = [];
  }
}

/** 驿站查询：全部筛选与分页都交给后端处理。 */
async function loadStationRows() {
  loading.stations = true;
  try {
    const page = await getStationPage({
      current: stationQuery.current,
      size: stationQuery.size,
      cityCode: userStore.cityCode || undefined,
      area: selectedArea.value === "全部区域" ? undefined : selectedArea.value,
      keyword: moduleKeyword.value || undefined,
      service: serviceType.value === "全部服务" ? undefined : serviceType.value,
      status: openOnly.value ? "OPEN" : undefined
    });
    stationRows.value = (page?.records || []).map(mapStation);
    stationTotal.value = Number(page?.total) || 0;
    updateStationNote();
    const kept = stationRows.value.find((item) => item.id === selectedDirectoryStation.value?.id);
    selectedDirectoryStation.value = kept || stationRows.value[0] || null;
  } catch (error) {
    ElMessage.error(errorMessage(error, "驿站列表加载失败"));
    stationRows.value = [];
    stationTotal.value = 0;
    selectedDirectoryStation.value = null;
  } finally {
    loading.stations = false;
  }
}

/** 搜索框与筛选条件变化时回到第 1 页再查询。 */
function searchStations() {
  stationQuery.current = 1;
  loadStationRows();
}

function onStationPageChange(page) {
  stationQuery.current = page;
  loadStationRows();
}

/** 服务预约 / 服务评价 / 我的收藏 的驿站下拉选项：按当前城市取前 50 条，避免一次拉取过多数据。 */
async function loadStationOptions() {
  try {
    const page = await getStationPage({ current: 1, size: 50, cityCode: userStore.cityCode || undefined });
    stationOptions.value = (page?.records || []).map(mapStation);
  } catch (error) {
    ElMessage.error(errorMessage(error, "驿站选项加载失败"));
    stationOptions.value = [];
  }
}

/**
 * 切换城市后重置驿站查询条件与页码并重新拉取。
 * 区域下拉与驿站列表都按城市维度统计，跨城市复用会得到空结果，所以一并重置。
 */
watch(() => userStore.cityCode, async () => {
  if (!userStore.cityCode) return;
  stationQuery.current = 1;
  selectedArea.value = "全部区域";
  stationAreaOptions.value = [];
  stationOptions.value = [];
  selectedDirectoryStation.value = null;
  await loadStationAreas();
  await loadStationRows();
  await loadStationOptions();
});

function selectStation(station) {
  selectedDirectoryStation.value = station;
}

async function loadStationServiceOptions(stationId) {
  try {
    return (await listStationServices(stationId)) || [];
  } catch {
    return [];
  }
}

// ------------------------------------------------------------------ 预约

async function loadMyAppointments() {
  loading.appointments = true;
  try {
    myAppointments.value = (await getMyAppointments()) || [];
  } catch (error) {
    ElMessage.error(errorMessage(error, "预约记录加载失败"));
    myAppointments.value = [];
  } finally {
    loading.appointments = false;
  }
}

async function onReservationStationChange(stationId) {
  const station = stationOptions.value.find((item) => item.id === stationId);
  reservationForm.stationName = station?.name || "";
  reservationOpenTime.value = station?.openTime || "";
  // 时段随营业时间变化；原选择不再合法时自动落到第一个可选时段。
  const slots = buildSlots(reservationOpenTime.value);
  if (!slots.includes(reservationForm.slot)) {
    reservationForm.slot = slots[0] || "";
  }
}

async function openReservation(station) {
  const target = station || selectedDirectoryStation.value || stationOptions.value[0];
  if (!target?.id) {
    ElMessage.warning("请先选择驿站");
    return;
  }
  reservationForm.stationId = target.id;
  reservationForm.stationName = target.name;
  reservationForm.date = reservationForm.date || new Date().toISOString().slice(0, 10);
  reservationOpenTime.value = target.openTime || "";
  const slots = buildSlots(reservationOpenTime.value);
  if (!slots.includes(reservationForm.slot)) {
    reservationForm.slot = slots[0] || "";
  }
  reservationVisible.value = true;
}

async function submitReservation() {
  if (!reservationForm.stationId || !reservationForm.date) {
    ElMessage.warning("请选择驿站和联系日期");
    return;
  }
  if (!reservationForm.subject.trim()) {
    ElMessage.warning("请填写想提前沟通的事项");
    return;
  }
  if (!reservationForm.slot) {
    ElMessage.warning("请选择方便联系的时段");
    return;
  }
  loading.submit = true;
  try {
    await createAppointment({
      stationId: reservationForm.stationId,
      // 服务项目改为用户自填的沟通事项，不再传 serviceId
      serviceName: reservationForm.subject.trim(),
      contactPhone: reservationForm.contactPhone.trim(),
      date: reservationForm.date,
      slot: reservationForm.slot
    });
    ElMessage.success("已提交，驿站管理员会联系你确认是否可行");
    reservationVisible.value = false;
    reservationForm.subject = "";
    reservationForm.contactPhone = "";
    await loadMyAppointments();
    messageStore.refreshQuietly();
  } catch (error) {
    ElMessage.error(errorMessage(error, "提交失败"));
  } finally {
    loading.submit = false;
  }
}

async function cancelMyAppointment(item) {
  try {
    await ElMessageBox.confirm(`确认取消「${item.serviceName}」的预约吗？`, "取消预约", { type: "warning" });
  } catch {
    return;
  }
  try {
    await cancelAppointment(item.id);
    ElMessage.success("预约已取消");
    await loadMyAppointments();
    messageStore.refreshQuietly();
  } catch (error) {
    ElMessage.error(errorMessage(error, "取消失败"));
  }
}

// ------------------------------------------------------------------ 诉求

async function loadMyAppeals() {
  loading.appeals = true;
  try {
    myAppeals.value = (await getMyAppeals()) || [];
    if (myAppeals.value.length > 0) {
      const current = selectedAppeal.value && myAppeals.value.find((item) => item.id === selectedAppeal.value.id);
      selectedAppeal.value = current || myAppeals.value[0];
    } else {
      selectedAppeal.value = null;
    }
  } catch (error) {
    ElMessage.error(errorMessage(error, "诉求记录加载失败"));
    myAppeals.value = [];
  } finally {
    loading.appeals = false;
  }
}

async function submitAppeal() {
  if (!appealForm.title || !appealForm.content) {
    ElMessage.warning("请先填写诉求标题和具体内容");
    return;
  }
  if (appealForm.receiverTarget === "STATION" && !appealForm.stationId) {
    ElMessage.warning("请选择要反馈的驿站，诉求才能直达该驿站");
    return;
  }
  loading.submit = true;
  try {
    // category 决定后端的状态文案（诉求「已办结」、表扬「已致谢」、建议「已回复」）
    await createAppeal({ ...appealForm, category: appealForm.category });
    ElMessage.success(`${appealCategoryMeta.value.label}已提交，可在「我的诉求」查看处理进度`);
    appealForm.title = "";
    appealForm.content = "";
    appealForm.stationId = null;
    await loadMyAppeals();
    messageStore.refreshQuietly();
  } catch (error) {
    ElMessage.error(errorMessage(error, "提交失败"));
  } finally {
    loading.submit = false;
  }
}

async function openAppealDetail(row) {
  try {
    selectedAppeal.value = await getAppealDetail(row.id);
  } catch (error) {
    ElMessage.error(errorMessage(error, "诉求详情加载失败"));
  }
}

function openEvaluationDialog(row) {
  appealEvaluationForm.appealId = row.id;
  appealEvaluationForm.score = 5;
  appealEvaluationForm.content = "";
  appealEvaluationVisible.value = true;
}

async function submitAppealEvaluation() {
  loading.submit = true;
  try {
    await evaluateAppeal({ ...appealEvaluationForm });
    ElMessage.success("评价已提交，感谢你的反馈");
    appealEvaluationVisible.value = false;
    await loadMyAppeals();
    messageStore.refreshQuietly();
  } catch (error) {
    ElMessage.error(errorMessage(error, "评价提交失败"));
  } finally {
    loading.submit = false;
  }
}

// ------------------------------------------------------------------ 活动

async function loadActivities() {  loading.activities = true;
  try {
    // 传当前城市：后端只返回「本市 + 全国性」，并把本市排在最前。
      // 与政策资讯同一套口径——不传会把其他城市的活动也混进来。
      const page = await getActivityPage({
        current: activityQuery.current,
        size: activityQuery.size,
        cityCode: userStore.cityCode || undefined
      });
    activityItems.value = page.records || [];
    activityTotal.value = page.total || 0;
    const mySignups = await getMySignups().catch(() => []);
    signedActivityIds.value = (mySignups || []).map((item) => item.activityId);
  } catch (error) {
    ElMessage.error(errorMessage(error, "活动列表加载失败"));
    activityItems.value = [];
    activityTotal.value = 0;
  } finally {
    loading.activities = false;
  }
}

/** 翻页：保持在当前页重新加载，报名成功后刷新也不会跳回第一页。 */
function onActivityPageChange(page) {
  activityQuery.current = page;
  loadActivities();
}

/**
 * 打开报名表单。
 *
 * 报名信息要交给承办方去联系报名人，所以姓名与电话必填、备注选填，
 * 不再是一键报名——那样驿站拿到一条没有联系方式的记录，等于没法联系。
 */
function openSignup(activity) {
  signupTarget.value = activity;
  signupForm.contactName = userStore.profile?.realName || userStore.profile?.nickname || "";
  signupForm.phone = userStore.profile?.phone || "";
  signupForm.remark = "";
  signupVisible.value = true;
}

/** 活动详情：直接复用列表记录（详情字段与列表一致），关闭详情再开报名表单，避免两层弹窗叠着。 */
function openActivityDetail(activity) {
  activeActivity.value = activity;
  activityDetailVisible.value = true;
}

function signupFromDetail(activity) {
  if (!activity) return;
  activityDetailVisible.value = false;
  openSignup(activity);
}

/** 活动城市标签：cityCode 为空表示全国性活动（后端约定），否则转成城市名。 */
function activityCityName(activity) {
  const code = String(activity?.cityCode || "").trim();
  return code ? cityNameOf(code) : "全国";
}

/**
 * 活动地点：采集活动的 address 是原文里的举办地，没有地址时说明「见原文」，
 * 不要用「地点待确认」——那会让用户以为本站能确认，其实本站只有原文这一条线索。
 */
function activityPlaceText(activity) {
  const place = String(activity?.address || "").trim() || String(activity?.stationName || "").trim();
  if (place) return place;
  return isExternalActivity(activity) ? "地点见原文" : "地点待确认";
}

/**
 * 活动时间：采集活动只有日期（时间为 00:00:00）时只显示日期。
 * 00:00:00 是「没有具体时刻」的占位，原样显示等于报了一个不存在的精确时间。
 */
function activityTimeText(activity) {
  const raw = String(activity?.activityTime || "").trim();
  if (!raw) return "时间待主办方确认";
  return raw.endsWith(" 00:00:00") ? raw.slice(0, 10) : raw;
}

/** 城市标签配色：当前城市的用品牌色标出，其他城市与全国性活动用中性色。 */
function activityCityTagType(activity) {
  const code = String(activity?.cityCode || "").trim();
  return code && code === userStore.cityCode ? "success" : "info";
}

/** 活动状态文案：采集活动本站不放号，写明「仅提供信息」而不是「报名中」。 */
function activityStatusText(activity) {
  if (isExternalActivity(activity)) return "仅提供信息";
  return activity?.remaining > 0 ? "报名中" : "已满";
}

function activityStatusTagType(activity) {
  if (isExternalActivity(activity)) return "info";
  return activity?.remaining > 0 ? "success" : "info";
}

/**
 * 详情正文：采集数据的 summary 与 content 常常是同一段话，重复渲染等于读两遍。
 * 与摘要相同（或没有正文）时返回空串，由摘要块单独承担展示。
 */
function activityBodyText(activity) {
  const summary = String(activity?.summary || "").trim();
  const content = String(activity?.content || "").trim();
  if (!content) return summary ? "" : "主办方暂未提供活动正文。";
  return content === summary ? "" : content;
}

/** 报名状态配色，与用户端语义一致。 */
function signupTagType(status) {
  if (status === "APPROVED") return "success";
  if (status === "REJECTED") return "danger";
  if (status === "CONTACTED") return "warning";
  return "info";
}

/**
 * 采集活动（第三方主办）的跳转：站内只做展示，报名与审核都在主办方那边完成。
 * 链接由后端保证非空，这里仍做一次兜底，避免点出一个空白窗口。
 */
function openExternalActivity(activity) {
  const url = externalUrlText(activity);
  if (!url) {
    ElMessage.warning("该活动未提供原文链接，暂无法跳转，可联系平台管理员核对");
    return;
  }
  window.open(url, "_blank", "noopener,noreferrer");
}

/** 我的报名：状态与承办方反馈。报名之后要能看到进展，否则等于石沉大海。 */
const mySignupsVisible = ref(false);
const mySignupsLoading = ref(false);
const mySignupsList = ref([]);

async function openMySignups() {
  mySignupsVisible.value = true;
  mySignupsLoading.value = true;
  try {
    mySignupsList.value = await getMySignups().catch(() => []);
  } finally {
    mySignupsLoading.value = false;
  }
}

/** 报名人撤销自己的报名：只能撤「已报名/已联系」，审核通过或未通过后由承办方处理。 */
async function cancelMySignup(item) {
  try {
    await ElMessageBox.confirm(`确认取消「${item.activityTitle || "该活动"}」的报名吗？`, "取消报名", {
      type: "warning",
      confirmButtonText: "取消报名",
      cancelButtonText: "再想想"
    });
  } catch {
    return;
  }
  try {
    await cancelSignup(item.id);
    ElMessage.success("报名已取消");
    mySignupsList.value = await getMySignups().catch(() => []);
    await loadActivities();
    messageStore.refreshQuietly();
  } catch (error) {
    ElMessage.error(errorMessage(error, "取消失败"));
  }
}

async function submitSignup() {
  const activity = signupTarget.value;
  if (!activity) return;
  if (!signupForm.contactName.trim()) {
    ElMessage.warning("请填写报名人姓名");
    return;
  }
  if (!signupForm.phone.trim()) {
    ElMessage.warning("请填写联系电话，承办方需要据此联系你");
    return;
  }
  signupSubmitting.value = true;
  try {
    await signupActivity({
      activityId: activity.id,
      title: activity.title,
      contactName: signupForm.contactName.trim(),
      phone: signupForm.phone.trim(),
      remark: signupForm.remark.trim()
    });
    signedActivityIds.value = [...signedActivityIds.value, activity.id];
    signupVisible.value = false;
    ElMessage.success(`已提交「${activity.title}」的报名，承办方会联系你`);
    await loadActivities();
    messageStore.refreshQuietly();
  } catch (error) {
    ElMessage.error(errorMessage(error, "报名失败"));
  } finally {
    signupSubmitting.value = false;
  }
}

// ------------------------------------------------------------------ 资讯

/** 切换筛选条件时回到第一页再加载，避免停在第 3 页却只剩 1 页数据。 */
function resetNewsAndLoad() {
  newsQuery.current = 1;
  loadNews();
}

async function loadNews() {
  loading.news = true;
  try {
    const category = newsFilter.value || undefined;
    const sourceType = newsSourceFilter.value === "全部"
      ? undefined
      : (newsSourceFilter.value === "平台" ? "PLATFORM" : "OFFICIAL");
    // 传当前城市：后端只返回「该城市 + 不限城市」，并把该城市的排在前面
    const cityCode = userStore.cityCode || undefined;
    const [newsPage, noticePage] = await Promise.all([
      getNewsPage({ current: newsQuery.current, size: newsQuery.size, category, cityCode, sourceType, platform: newsPlatformFilter.value || undefined }),
      // 通知公告不属于政策来源体系，按分类单独拉取
      sourceType === "PLATFORM"
        ? Promise.resolve({ records: [], total: 0 })
        : getNoticePage({ current: newsQuery.current, size: newsQuery.size, noticeType: category })
    ]);
    const merged = [...(newsPage?.records || []), ...(noticePage?.records || [])];
    // 不能只按日期排——那样会把后端算好的「本城市优先」打乱。
    // 这里先按「是否属于当前城市」分组，再在组内按发布日期倒序。
    merged.sort((left, right) => {
      const leftLocal = left.cityCode && left.cityCode === cityCode ? 0 : 1;
      const rightLocal = right.cityCode && right.cityCode === cityCode ? 0 : 1;
      if (leftLocal !== rightLocal) return leftLocal - rightLocal;
      const leftDate = String(left.publishedAt || left.date || "");
      const rightDate = String(right.publishedAt || right.date || "");
      return rightDate.localeCompare(leftDate);
    });
    newsItems.value = merged;
    newsTotal.value = (newsPage?.total || 0) + (noticePage?.total || 0);
  } catch (error) {
    ElMessage.error(errorMessage(error, "资讯加载失败"));
    newsItems.value = [];
    newsTotal.value = 0;
  } finally {
    loading.news = false;
  }
}

function onNewsPageChange(page) {
  newsQuery.current = page;
  loadNews();
}

function openNewsDetail(item) {
  activeNews.value = item;
  newsVisible.value = true;
}

// ------------------------------------------------------------------ 评价

async function loadStationEvaluations() {
  if (!evaluationForm.stationId) return;
  loading.evaluations = true;
  try {
    const [page, summary] = await Promise.all([
      getStationEvaluationPage({ current: 1, size: 10, stationId: evaluationForm.stationId }),
      getStationEvaluationSummary(evaluationForm.stationId)
    ]);
    stationEvaluations.value = page?.records || [];
    evaluationSummary.value = summary || { count: 0, averageScore: 0 };
  } catch (error) {
    ElMessage.error(errorMessage(error, "评价加载失败"));
    stationEvaluations.value = [];
  } finally {
    loading.evaluations = false;
  }
}

async function submitEvaluation() {
  if (!evaluationForm.stationId) {
    ElMessage.warning("请选择要评价的驿站");
    return;
  }
  if (!evaluationForm.content.trim()) {
    ElMessage.warning("请填写服务评价");
    return;
  }
  loading.submit = true;
  try {
    await createStationEvaluation({ ...evaluationForm });
    ElMessage.success("评价已提交，感谢你的反馈");
    evaluationForm.content = "";
    await loadStationEvaluations();
  } catch (error) {
    ElMessage.error(errorMessage(error, "评价提交失败"));
  } finally {
    loading.submit = false;
  }
}

// ------------------------------------------------------------------ 统计

async function loadStatistics() {
  loading.stat = true;
  try {
    const [overview, ranking, trend] = await Promise.all([
      getStatOverview(),
      getServiceRanking(),
      getMonthlyTrend()
    ]);
    statOverview.value = overview || {};
    rankingRows.value = ranking || [];
    const months = trend?.months || [];
    trendRows.value = months.map((month, index) => ({
      month,
      appointments: trend.appointments?.[index] ?? 0,
      appeals: trend.appeals?.[index] ?? 0,
      signups: trend.signups?.[index] ?? 0,
      finishRate: trend.finishRate?.[index] ?? 0
    }));
  } catch (error) {
    ElMessage.error(errorMessage(error, "统计数据加载失败"));
  } finally {
    loading.stat = false;
  }
}

async function exportMyData() {
  ElMessageBox.alert(
    "报表导出属于管理端能力（需要管理员角色）。你可以在“管理端 → 报表导出”下载诉求、预约、驿站、报名与评价明细。",
    "导出说明",
    { type: "info", confirmButtonText: "知道了" }
  );
}

// ------------------------------------------------------------------ 个人中心

async function loadProfile() {
  loading.profile = true;
  try {
    const info = await currentUser();
    Object.assign(profileForm, {
      username: info.username || "",
      realName: info.realName || "",
      phone: info.phone || "",
      jobType: info.jobType || "",
      area: info.area || "",
      roleName: info.roleName || ""
    });
  } catch (error) {
    ElMessage.error(errorMessage(error, "个人资料加载失败"));
  } finally {
    loading.profile = false;
  }
}

async function saveProfile() {
  loading.submit = true;
  try {
    await updateProfile({
      realName: profileForm.realName,
      phone: profileForm.phone,
      jobType: profileForm.jobType,
      area: profileForm.area
    });
    ElMessage.success("个人资料已保存");
    userStore.user.name = profileForm.realName || userStore.user.name;
  } catch (error) {
    ElMessage.error(errorMessage(error, "保存失败"));
  } finally {
    loading.submit = false;
  }
}

// ------------------------------------------------------------------ 顶栏动作

async function handlePrimary() {
  const action = currentConfig.value.action;
  switch (props.view) {
    case "驿站查询":
      await loadStationRows();
      ElMessage.success("驿站列表已刷新");
      break;
    case "服务预约":
      await loadMyAppointments();
      ElMessage.success("预约记录已刷新");
      break;
    case "诉求与反馈":
      ElMessage.info("诉求会按类型分派：驿站服务与设施建议由驿站管理员处理，其余由平台协调。");
      break;
    case "我的诉求":
      await loadMyAppeals();
      ElMessage.success("诉求进度已刷新");
      break;
    case "活动报名": {
      // 用户需要看到自己的报名进展与承办方反馈，而不只是一句「已报名 N 场」。
      await openMySignups();
      break;
    }
    case "政策资讯":
      await loadNews();
      ElMessage.success("资讯已刷新");
      break;
    case "服务评价": {
      const mine = await getMyEvaluations().catch(() => []);
      ElMessage.success(mine.length ? `你已提交 ${mine.length} 条评价` : "还没有提交过评价");
      break;
    }
    case "消息中心":
      markMessagesRead();
      break;
    case "数据统计":
      await loadStatistics();
      ElMessage.success("统计数据已刷新");
      break;
    case "我的收藏":
      ElMessage.info(`已收藏 ${favorites.value.length} 个驿站`);
      break;
    default:
      await loadProfile();
      ElMessage.success("资料已刷新");
  }
}

// ------------------------------------------------------------------ 视图调度

async function loadView() {
  const view = props.view;
  // 服务预约 / 服务评价 / 我的收藏 的驿站选项只按当前城市加载前 50 条
  if (["服务预约", "我的收藏", "服务评价"].includes(view) && stationOptions.value.length === 0) {
    await loadStationOptions();
  }
  // 区域候选来自 /api/station/areas（按当前城市统计），驿站查询、诉求反馈与个人中心共同使用
  if (["驿站查询", "诉求与反馈", "个人中心"].includes(view) && stationAreaOptions.value.length === 0) {
    await loadStationAreas();
  }
  switch (view) {
    case "驿站查询":
      await loadStationRows();
      break;
    case "服务预约":
      dataNote.value = "预约提交后会写入服务端并按“待驿站确认 → 已确认/已拒绝”流转，可在下方查看本人预约记录。";
      await loadMyAppointments();
      if (!reservationForm.stationId && stationOptions.value.length) {
        await onReservationStationChange(stationOptions.value[0].id);
        reservationForm.stationId = stationOptions.value[0].id;
        reservationForm.date = new Date().toISOString().slice(0, 10);
      }
      break;
    case "诉求与反馈":
      dataNote.value = "反馈提交后会生成处理记录。反馈给驿站由驿站管理员受理，反馈给平台由平台管理员受理，回复可在“我的诉求”中查看。";
      // 区域候选按当前城市返回，未选择时默认落到第一个可用区域，避免提交空区域
      if (!appealForm.area) {
        appealForm.area = areaOptions.value[0] || "";
      }
      await loadMyAppeals();
      break;
    case "我的诉求":
      dataNote.value = "";
      await loadMyAppeals();
      break;
    case "活动报名":
      dataNote.value =
        "活动名额由服务端校验，名额已满或重复报名会被拒绝；驿站自办活动可在站内报名。" +
        "带「政府群团 / 官方媒体」标签的活动信息采集自公开来源，含全国性及其他城市的活动，" +
        "报名与审核由主办方负责，本站仅提供信息与跳转。";
      await loadActivities();
      break;
    case "政策资讯":
      dataNote.value = "资讯与公告分别来自 /api/news 与 /api/notice，正文以发布内容为准。";
      await loadNews();
      break;
    case "服务评价":
      dataNote.value = "评价写入后会参与驿站平均分统计（综合、服务、环境、设施四个维度）。";
      if (!evaluationForm.stationId && stationOptions.value.length) {
        evaluationForm.stationId = stationOptions.value[0].id;
      }
      await loadStationEvaluations();
      break;
    case "消息中心":
      dataNote.value = "预约审核、报名审核、诉求处理、变更审批和暂停服务等状态变化会在这里提醒；已读状态保存在账号中。";
      await Promise.all([loadMyAppointments(), loadMyAppeals(), loadActivities()]);
      break;
    case "数据统计":
      dataNote.value = "统计口径来自后端实时聚合，不使用固定演示值。";
      await loadStatistics();
      break;
    case "我的收藏":
      dataNote.value = "收藏保存在本机浏览器，可随时取消。";
      break;
    default:
      dataNote.value = "";
      await loadProfile();
  }
}

watch(() => props.view, loadView);

/**
 * 首页点「提前联系」后带着驿站 id 切过来：预选该驿站并打开表单。
 * 处理完通知父组件清空，避免返回首页再进来时重复弹出。
 */
watch(
  () => props.prefillStationId,
  async (id) => {
    if (id === null || id === undefined) return;
    const station = stationOptions.value.find((item) => String(item.id) === String(id));
    if (station) {
      await openReservation(station);
    }
    // 无论是否匹配到，都要通知父组件清空，避免下次进入重复处理。
    emit("prefill-consumed");
  }
);

onMounted(loadView);
</script>

<style scoped>
.station-pagination {
  margin-top: 4px;
}

.station-source {
  margin-bottom: 18px;
  padding-top: 14px;
  border-top: 1px dashed var(--line);
}

.station-source-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #556172;
  font-size: 13px;
}

.station-source-list {
  margin: 8px 0 10px;
}

.station-source-list a {
  color: var(--primary);
  word-break: break-all;
}

/* 驿站详情里的「在高德地图中搜索该驿站」外链，与其他来源链接保持一致的强调色。 */
.detail-list a {
  color: var(--primary);
  word-break: break-all;
}

.station-source-date {
  margin: 0 0 10px;
  color: var(--muted);
  font-size: 12px;
}

.station-source-note {
  margin-top: 2px;
}
</style>
