<template>
  <div class="fault-code-manage">
    <!-- 筛选栏 -->
    <div class="filter-bar">
      <el-select
        v-model="queryParams.severity"
        placeholder="严重程度"
        clearable
        style="width: 140px"
        @change="handleSearch"
      >
        <el-option label="致命" value="critical" />
        <el-option label="严重" value="major" />
        <el-option label="一般" value="minor" />
        <el-option label="提示" value="info" />
      </el-select>
      <el-select
        v-model="queryParams.equipmentType"
        placeholder="设备类型"
        clearable
        style="width: 160px; margin-left: 12px"
        @change="handleSearch"
      >
        <el-option
          v-for="item in equipmentTypeOptions"
          :key="item"
          :label="item"
          :value="item"
        />
      </el-select>
      <el-input
        v-model="queryParams.keyword"
        placeholder="搜索故障代码或描述"
        clearable
        style="width: 220px; margin-left: 12px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button type="primary" style="margin-left: 12px" @click="handleSearch">搜索</el-button>
      <el-button @click="handleReset">重置</el-button>

      <el-button type="primary" style="margin-left: auto" @click="handleAdd">
        <el-icon><Plus /></el-icon>新增故障代码
      </el-button>
    </div>

    <!-- 数据表格 -->
    <el-table
      v-loading="loading"
      :data="tableData"
      border
      stripe
      style="width: 100%; margin-top: 16px"
    >
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="code" label="故障代码" width="140" align="center">
        <template #default="{ row }">
          <span class="fault-code">{{ row.code }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="故障描述" min-width="220" show-overflow-tooltip />
      <el-table-column prop="severity" label="严重程度" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="getSeverityType(row.severity)" size="small">
            {{ getSeverityLabel(row.severity) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="equipmentType" label="设备类型" width="130" align="center" />
      <el-table-column prop="solution" label="处理方案" min-width="200" show-overflow-tooltip />
      <el-table-column label="操作" width="160" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="handleViewSolution(row)">查看方案</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSearch"
        @current-change="handleSearch"
      />
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑故障代码' : '新增故障代码'"
      width="600px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="90px"
      >
        <el-form-item label="故障代码" prop="code">
          <el-input v-model="formData.code" placeholder="如 E001、F-1001" />
        </el-form-item>
        <el-form-item label="故障描述" prop="description">
          <el-input
            v-model="formData.description"
            type="textarea"
            :rows="2"
            placeholder="请描述故障现象"
          />
        </el-form-item>
        <el-form-item label="严重程度" prop="severity">
          <el-select v-model="formData.severity" placeholder="请选择" style="width: 100%">
            <el-option label="致命" value="critical" />
            <el-option label="严重" value="major" />
            <el-option label="一般" value="minor" />
            <el-option label="提示" value="info" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备类型" prop="equipmentType">
          <el-input v-model="formData.equipmentType" placeholder="如：数控机床、注塑机" />
        </el-form-item>
        <el-form-item label="处理方案" prop="solution">
          <el-input
            v-model="formData.solution"
            type="textarea"
            :rows="4"
            placeholder="请输入处理方案（可关联多个步骤）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 查看方案对话框 -->
    <el-dialog
      v-model="solutionDialogVisible"
      title="处理方案详情"
      width="520px"
    >
      <div v-if="currentFault" class="solution-detail">
        <div class="solution-header">
          <el-tag :type="getSeverityType(currentFault.severity)" size="small">
            {{ getSeverityLabel(currentFault.severity) }}
          </el-tag>
          <span class="solution-code">{{ currentFault.code }}</span>
          <span class="solution-desc">{{ currentFault.description }}</span>
        </div>
        <el-divider />
        <div class="solution-body">
          <h4>处理方案</h4>
          <p style="white-space: pre-wrap; line-height: 1.8">{{ currentFault.solution || '暂无处理方案' }}</p>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus';
import { Search, Plus } from '@element-plus/icons-vue';
import {
  listFaultCode,
  addFaultCode,
  updateFaultCode,
  deleteFaultCode,
  type FaultCode,
  type FaultCodeQuery
} from '@/api/industrial';

const equipmentTypeOptions = ['数控机床', '注塑机', 'CNC加工中心', 'PLC控制器', '工业机器人', '传送系统', '压缩机'];

const severityMap: Record<string, { label: string; type: string }> = {
  critical: { label: '致命', type: 'danger' },
  major: { label: '严重', type: 'warning' },
  minor: { label: '一般', type: '' },
  info: { label: '提示', type: 'info' }
};

const getSeverityLabel = (val: string) => severityMap[val]?.label || val;
const getSeverityType = (val: string) => (severityMap[val]?.type || '') as any;

// 查询与列表
const loading = ref(false);
const tableData = ref<FaultCode[]>([]);
const total = ref(0);
const queryParams = reactive<FaultCodeQuery>({
  pageNum: 1,
  pageSize: 20,
  severity: '',
  equipmentType: '',
  keyword: ''
});

const fetchList = async () => {
  loading.value = true;
  try {
    const res: any = await listFaultCode(queryParams);
    tableData.value = res.rows || res.data?.list || [];
    total.value = res.total || res.data?.total || 0;
  } catch {
    tableData.value = [];
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  queryParams.pageNum = 1;
  fetchList();
};

const handleReset = () => {
  queryParams.severity = '';
  queryParams.equipmentType = '';
  queryParams.keyword = '';
  queryParams.pageNum = 1;
  fetchList();
};

// 表单
const dialogVisible = ref(false);
const isEdit = ref(false);
const submitLoading = ref(false);
const formRef = ref<FormInstance>();
const formData = reactive<FaultCode>({
  id: undefined,
  code: '',
  description: '',
  severity: '',
  equipmentType: '',
  solution: ''
});

const formRules = {
  code: [{ required: true, message: '请输入故障代码', trigger: 'blur' }],
  description: [{ required: true, message: '请输入故障描述', trigger: 'blur' }],
  severity: [{ required: true, message: '请选择严重程度', trigger: 'change' }],
  equipmentType: [{ required: true, message: '请输入设备类型', trigger: 'blur' }]
};

const resetForm = () => {
  formData.id = undefined;
  formData.code = '';
  formData.description = '';
  formData.severity = '';
  formData.equipmentType = '';
  formData.solution = '';
};

const handleAdd = () => {
  resetForm();
  isEdit.value = false;
  dialogVisible.value = true;
};

const handleEdit = (row: FaultCode) => {
  Object.assign(formData, row);
  isEdit.value = true;
  dialogVisible.value = true;
};

const handleSubmit = async () => {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;

  submitLoading.value = true;
  try {
    if (isEdit.value) {
      await updateFaultCode(formData);
      ElMessage.success('编辑成功');
    } else {
      await addFaultCode(formData);
      ElMessage.success('新增成功');
    }
    dialogVisible.value = false;
    fetchList();
  } catch {
    // handled
  } finally {
    submitLoading.value = false;
  }
};

const handleDelete = (row: FaultCode) => {
  ElMessageBox.confirm(`确定删除故障代码 "${row.code}"？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteFaultCode(row.id!);
    ElMessage.success('删除成功');
    fetchList();
  }).catch(() => {});
};

// 查看方案
const solutionDialogVisible = ref(false);
const currentFault = ref<FaultCode | null>(null);

const handleViewSolution = (row: FaultCode) => {
  currentFault.value = row;
  solutionDialogVisible.value = true;
};

onMounted(() => {
  fetchList();
});
</script>

<style scoped>
.fault-code-manage {
  padding: 20px;
}
.filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}
.fault-code {
  font-family: 'Courier New', monospace;
  font-weight: 700;
  color: #f56c6c;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.solution-detail {
  padding: 0 8px;
}
.solution-header {
  display: flex;
  align-items: center;
  gap: 12px;
}
.solution-code {
  font-family: 'Courier New', monospace;
  font-weight: 700;
  font-size: 16px;
  color: #303133;
}
.solution-desc {
  color: #606266;
}
.solution-body h4 {
  margin: 0 0 12px 0;
  color: #303133;
}
.solution-body p {
  color: #606266;
  margin: 0;
}
</style>
