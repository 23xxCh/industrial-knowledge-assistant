<template>
  <div class="qa-manage">
    <!-- 搜索与筛选区域 -->
    <div class="filter-bar">
      <el-select
        v-model="queryParams.category"
        placeholder="按分类筛选"
        clearable
        style="width: 180px"
        @change="handleSearch"
      >
        <el-option
          v-for="item in categoryOptions"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </el-select>
      <el-input
        v-model="queryParams.keyword"
        placeholder="搜索问题或答案关键词"
        clearable
        style="width: 260px; margin-left: 12px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button type="primary" style="margin-left: 12px" @click="handleSearch">
        <el-icon><Search /></el-icon>搜索
      </el-button>
      <el-button @click="handleReset">重置</el-button>

      <div class="filter-right">
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :show-file-list="false"
          accept=".csv"
          :on-change="handleFileChange"
        >
          <el-button type="success" plain>
            <el-icon><Upload /></el-icon>批量导入 CSV
          </el-button>
        </el-upload>
        <el-button type="primary" style="margin-left: 12px" @click="handleAdd">
          <el-icon><Plus /></el-icon>新增 QA 对
        </el-button>
      </div>
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
      <el-table-column prop="question" label="问题" min-width="200" show-overflow-tooltip />
      <el-table-column prop="answer" label="答案" min-width="280" show-overflow-tooltip />
      <el-table-column prop="category" label="分类" width="130" align="center">
        <template #default="{ row }">
          <el-tag :type="getCategoryTagType(row.category)" size="small">
            {{ getCategoryLabel(row.category) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="equipmentType" label="设备类型" width="130" align="center" />
      <el-table-column prop="tags" label="标签" width="180">
        <template #default="{ row }">
          <el-tag
            v-for="tag in parseTags(row.tags)"
            :key="tag"
            size="small"
            type="info"
            style="margin: 2px 4px 2px 0"
          >
            {{ tag }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
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
      :title="isEdit ? '编辑 QA 对' : '新增 QA 对'"
      width="640px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="90px"
      >
        <el-form-item label="问题" prop="question">
          <el-input
            v-model="formData.question"
            type="textarea"
            :rows="2"
            placeholder="请输入问题"
          />
        </el-form-item>
        <el-form-item label="答案" prop="answer">
          <el-input
            v-model="formData.answer"
            type="textarea"
            :rows="4"
            placeholder="请输入答案"
          />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="formData.category" placeholder="请选择分类" style="width: 100%">
            <el-option
              v-for="item in categoryOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="设备类型" prop="equipmentType">
          <el-input v-model="formData.equipmentType" placeholder="如：数控机床、注塑机" />
        </el-form-item>
        <el-form-item label="标签" prop="tags">
          <el-input v-model="formData.tags" placeholder="多个标签用逗号分隔" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { ElMessage, ElMessageBox, type FormInstance, type UploadFile } from 'element-plus';
import { Search, Plus, Upload } from '@element-plus/icons-vue';
import { listQA, addQA, updateQA, deleteQA, importQA, type QAPair, type QAQuery } from '@/api/industrial';

// 分类选项
const categoryOptions = [
  { label: '设备故障', value: 'equipment_fault' },
  { label: '工艺参数', value: 'process_params' },
  { label: '操作规范', value: 'operation_spec' },
  { label: '安全规程', value: 'safety_regulation' },
  { label: '维护保养', value: 'maintenance' }
];

const categoryMap: Record<string, string> = {
  equipment_fault: '设备故障',
  process_params: '工艺参数',
  operation_spec: '操作规范',
  safety_regulation: '安全规程',
  maintenance: '维护保养'
};

const getCategoryLabel = (val: string) => categoryMap[val] || val;
const getCategoryTagType = (val: string) => {
  const map: Record<string, string> = {
    equipment_fault: 'danger',
    process_params: '',
    operation_spec: 'success',
    safety_regulation: 'warning',
    maintenance: 'info'
  };
  return (map[val] || '') as any;
};

const parseTags = (tags?: string) => {
  if (!tags) return [];
  return tags.split(/[,，]/).map(t => t.trim()).filter(Boolean);
};

// 查询与列表
const loading = ref(false);
const tableData = ref<QAPair[]>([]);
const total = ref(0);
const queryParams = reactive<QAQuery>({
  pageNum: 1,
  pageSize: 20,
  category: '',
  keyword: ''
});

const fetchList = async () => {
  loading.value = true;
  try {
    const res: any = await listQA(queryParams);
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
  queryParams.category = '';
  queryParams.keyword = '';
  queryParams.pageNum = 1;
  fetchList();
};

// 表单
const dialogVisible = ref(false);
const isEdit = ref(false);
const submitLoading = ref(false);
const formRef = ref<FormInstance>();
const formData = reactive<QAPair>({
  id: undefined,
  question: '',
  answer: '',
  category: '',
  equipmentType: '',
  tags: ''
});

const formRules = {
  question: [{ required: true, message: '请输入问题', trigger: 'blur' }],
  answer: [{ required: true, message: '请输入答案', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }]
};

const resetForm = () => {
  formData.id = undefined;
  formData.question = '';
  formData.answer = '';
  formData.category = '';
  formData.equipmentType = '';
  formData.tags = '';
};

const handleAdd = () => {
  resetForm();
  isEdit.value = false;
  dialogVisible.value = true;
};

const handleEdit = (row: QAPair) => {
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
      await updateQA(formData);
      ElMessage.success('编辑成功');
    } else {
      await addQA(formData);
      ElMessage.success('新增成功');
    }
    dialogVisible.value = false;
    fetchList();
  } catch {
    // error handled by interceptor
  } finally {
    submitLoading.value = false;
  }
};

const handleDelete = (row: QAPair) => {
  ElMessageBox.confirm(`确定删除该 QA 对？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await deleteQA(row.id!);
    ElMessage.success('删除成功');
    fetchList();
  }).catch(() => {});
};

// CSV 导入
const uploadRef = ref();
const handleFileChange = async (file: UploadFile) => {
  if (!file.raw) return;
  try {
    await importQA(file.raw);
    ElMessage.success('导入成功');
    fetchList();
  } catch {
    // handled
  }
};

onMounted(() => {
  fetchList();
});
</script>

<style scoped>
.qa-manage {
  padding: 20px;
}
.filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0;
}
.filter-right {
  margin-left: auto;
  display: flex;
  align-items: center;
}
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
