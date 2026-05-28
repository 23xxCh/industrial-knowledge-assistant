<template>
  <div class="equipment-params">
    <el-row :gutter="20">
      <!-- 左侧：设备列表 -->
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>设备列表</span>
              <el-input
                v-model="searchKeyword"
                placeholder="搜索设备"
                clearable
                size="small"
                style="width: 160px"
                @input="handleEquipmentSearch"
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
            </div>
          </template>
          <el-table
            v-loading="equipmentLoading"
            :data="equipmentList"
            highlight-current-row
            style="width: 100%"
            @row-click="handleEquipmentClick"
            size="small"
          >
            <el-table-column prop="equipmentCode" label="设备编号" width="110" />
            <el-table-column prop="equipmentName" label="设备名称" min-width="120" show-overflow-tooltip />
            <el-table-column prop="equipmentType" label="类型" width="80" />
            <el-table-column prop="status" label="状态" width="70" align="center">
              <template #default="{ row }">
                <el-tag
                  :type="row.status === 'running' ? 'success' : row.status === 'fault' ? 'danger' : 'info'"
                  size="small"
                >
                  {{ statusMap[row.status] || row.status }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 右侧：参数详情 -->
      <el-col :span="16">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>
                参数详情
                <span v-if="currentEquipment" class="equipment-info">
                  — {{ currentEquipment.equipmentName }}（{{ currentEquipment.equipmentCode }}）
                </span>
              </span>
            </div>
          </template>

          <div v-if="!currentEquipment" class="empty-tip">
            <el-empty description="请在左侧选择一台设备" />
          </div>

          <template v-else>
            <el-table
              v-loading="paramsLoading"
              :data="paramList"
              border
              stripe
              style="width: 100%"
            >
              <el-table-column type="index" label="序号" width="60" align="center" />
              <el-table-column prop="paramName" label="参数名称" min-width="150" />
              <el-table-column prop="targetValue" label="目标值" width="100" align="center">
                <template #default="{ row }">
                  <span class="target-value">{{ row.targetValue }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="lowerLimit" label="下限" width="90" align="center" />
              <el-table-column prop="upperLimit" label="上限" width="90" align="center" />
              <el-table-column prop="unit" label="单位" width="70" align="center" />
              <el-table-column label="操作" width="80" align="center">
                <template #default="{ row }">
                  <el-button link type="primary" @click="handleEditParam(row)">编辑</el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>
        </el-card>
      </el-col>
    </el-row>

    <!-- 参数编辑对话框 -->
    <el-dialog
      v-model="paramDialogVisible"
      title="编辑参数"
      width="480px"
      destroy-on-close
    >
      <el-form
        ref="paramFormRef"
        :model="paramForm"
        :rules="paramRules"
        label-width="80px"
      >
        <el-form-item label="参数名称">
          <el-input v-model="paramForm.paramName" disabled />
        </el-form-item>
        <el-form-item label="目标值" prop="targetValue">
          <el-input v-model="paramForm.targetValue" placeholder="请输入目标值" />
        </el-form-item>
        <el-form-item label="下限" prop="lowerLimit">
          <el-input v-model="paramForm.lowerLimit" placeholder="请输入下限" />
        </el-form-item>
        <el-form-item label="上限" prop="upperLimit">
          <el-input v-model="paramForm.upperLimit" placeholder="请输入上限" />
        </el-form-item>
        <el-form-item label="单位" prop="unit">
          <el-input v-model="paramForm.unit" placeholder="如 ℃、MPa、rpm" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="paramDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="paramSubmitLoading" @click="handleParamSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { ElMessage, type FormInstance } from 'element-plus';
import { Search } from '@element-plus/icons-vue';
import {
  listEquipment,
  listEquipmentParams,
  updateEquipmentParam,
  type Equipment,
  type EquipmentParam
} from '@/api/industrial';

const statusMap: Record<string, string> = {
  running: '运行',
  stopped: '停机',
  fault: '故障',
  maintenance: '维护'
};

// 设备列表
const equipmentLoading = ref(false);
const equipmentList = ref<Equipment[]>([]);
const searchKeyword = ref('');
const currentEquipment = ref<Equipment | null>(null);

const fetchEquipment = async () => {
  equipmentLoading.value = true;
  try {
    const res: any = await listEquipment({ keyword: searchKeyword.value });
    equipmentList.value = res.data || res.rows || [];
  } catch {
    equipmentList.value = [];
  } finally {
    equipmentLoading.value = false;
  }
};

const handleEquipmentSearch = () => {
  fetchEquipment();
};

// 设备参数
const paramsLoading = ref(false);
const paramList = ref<EquipmentParam[]>([]);

const handleEquipmentClick = async (row: Equipment) => {
  currentEquipment.value = row;
  paramsLoading.value = true;
  try {
    const res: any = await listEquipmentParams(row.id!);
    paramList.value = res.data || res.rows || [];
  } catch {
    paramList.value = [];
  } finally {
    paramsLoading.value = false;
  }
};

// 参数编辑
const paramDialogVisible = ref(false);
const paramSubmitLoading = ref(false);
const paramFormRef = ref<FormInstance>();
const paramForm = reactive<EquipmentParam>({
  id: undefined,
  equipmentId: undefined,
  paramName: '',
  targetValue: '',
  upperLimit: '',
  lowerLimit: '',
  unit: ''
});

const paramRules = {
  targetValue: [{ required: true, message: '请输入目标值', trigger: 'blur' }]
};

const handleEditParam = (row: EquipmentParam) => {
  Object.assign(paramForm, row);
  paramDialogVisible.value = true;
};

const handleParamSubmit = async () => {
  const valid = await paramFormRef.value?.validate().catch(() => false);
  if (!valid) return;

  paramSubmitLoading.value = true;
  try {
    await updateEquipmentParam(paramForm);
    ElMessage.success('参数更新成功');
    paramDialogVisible.value = false;
    // 刷新参数列表
    if (currentEquipment.value) {
      handleEquipmentClick(currentEquipment.value);
    }
  } catch {
    // handled
  } finally {
    paramSubmitLoading.value = false;
  }
};

onMounted(() => {
  fetchEquipment();
});
</script>

<style scoped>
.equipment-params {
  padding: 20px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.equipment-info {
  font-size: 13px;
  color: #909399;
  font-weight: normal;
}
.empty-tip {
  padding: 40px 0;
}
.target-value {
  font-weight: 600;
  color: #409eff;
}
</style>
