package com.bioplatform.agent.tools.impl;

import com.bioplatform.agent.tools.Tool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 格式信息工具 - 返回生物信息学文件格式的详细说明
 *
 * @author luosg
 */
@Component
public class FormatInfoTool implements Tool {

    private final ObjectMapper objectMapper;

    public FormatInfoTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getName() {
        return "format_info";
    }

    @Override
    public String getDescription() {
        return "查询生物信息学文件格式的详细说明，包括格式用途、结构、常用工具和注意事项。" +
                "支持VCF、BAM、SAM、FASTA、FASTQ、BED、GFF、GTF、BigWig等格式。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> formatProp = new HashMap<>();
        formatProp.put("type", "string");
        formatProp.put("description", "文件格式名称，如: VCF, BAM, FASTA, FASTQ, BED, GFF, GTF 等");
        properties.put("format", formatProp);

        schema.put("properties", properties);

        java.util.List<String> required = new java.util.ArrayList<>();
        required.add("format");
        schema.put("required", required);

        return schema;
    }

    @Override
    public String execute(Map<String, String> args) {
        try {
            String format = args.get("format");
            if (format == null || format.isEmpty()) {
                return "{\"error\": \"缺少必需参数 format\"}";
            }

            Map<String, Object> info = getFormatInfo(format.toUpperCase());
            return objectMapper.writeValueAsString(info);
        } catch (Exception e) {
            return "{\"error\": \"查询格式信息失败: " + e.getMessage() + "\"}";
        }
    }

    private Map<String, Object> getFormatInfo(String format) {
        Map<String, Object> info = new HashMap<>();
        info.put("format", format);

        switch (format) {
            case "VCF" -> {
                info.put("full_name", "Variant Call Format");
                info.put("description", "变异调用格式，用于存储基因组变异信息（SNP、Indel、SV等）");
                info.put("extension", ".vcf / .vcf.gz");
                info.put("structure", "由header行（##开头）和数据列组成。数据列包含：CHROM, POS, ID, REF, ALT, QUAL, FILTER, INFO, FORMAT, 样本列");
                info.put("common_tools", "GATK, FreeBayes, bcftools, VCFtools, PLINK");
                info.put("notes", "VCF 4.2是当前主流版本。压缩的VCF(.vcf.gz)推荐使用bcftools操作。");
            }
            case "BAM" -> {
                info.put("full_name", "Binary Alignment/Map");
                info.put("description", "二进制比对格式，存储reads比对到参考基因组的信息，是SAM的压缩格式");
                info.put("extension", ".bam");
                info.put("structure", "二进制格式，包含header和alignment records。需要索引文件(.bai)进行随机访问");
                info.put("common_tools", "samtools, Picard, GATK, IGV");
                info.put("notes", "BAM文件必须按坐标排序才能建立索引。推荐使用samtools进行操作。");
            }
            case "SAM" -> {
                info.put("full_name", "Sequence Alignment/Map");
                info.put("description", "文本格式的序列比对文件");
                info.put("extension", ".sam");
                info.put("structure", "以@开头的header行和以制表符分隔的数据行，包含11个标准列");
                info.put("common_tools", "samtools, Picard");
                info.put("notes", "SAM是文本格式，文件较大，通常需要转换为BAM格式使用。");
            }
            case "FASTA", "FA", "FAA", "FNA" -> {
                info.put("full_name", "FASTA Sequence Format");
                info.put("description", "序列文件格式，存储DNA或蛋白质序列");
                info.put("extension", ".fasta, .fa, .fna, .faa");
                info.put("structure", "以>开头的描述行，后跟序列数据行");
                info.put("common_tools", "BLAST, BWA, HISAT2, samtools faidx");
                info.put("notes", "可被gzip压缩为.fasta.gz。参考基因组通常以FASTA格式存储。");
            }
            case "FASTQ", "FQ" -> {
                info.put("full_name", "FASTQ Sequence Format");
                info.put("description", "测序原始数据格式，包含序列和对应的碱基质量值");
                info.put("extension", ".fastq, .fq");
                info.put("structure", "四行一组：@序列ID, 序列, +, 质量值（ASCII编码的Phred分数）");
                info.put("common_tools", "FastQC, Trimmomatic, fastp, Cutadapt");
                info.put("notes", "原始测序数据的标准格式。双端测序有两个文件(_1/_2)。质量值使用Phred+33编码。");
            }
            case "BED" -> {
                info.put("full_name", "Browser Extensible Data");
                info.put("description", "基因组区间文件格式，定义基因组上的区域");
                info.put("extension", ".bed");
                info.put("structure", "至少3列：chrom, chromStart, chromEnd。可选列包括name, score, strand等");
                info.put("common_tools", "bedtools, BEDOPS, UCSC Genome Browser");
                info.put("notes", "区间是0-based起始，1-based结束。常用于定义peak区域、基因区间等。");
            }
            case "GFF", "GTF" -> {
                info.put("full_name", "General Feature Format / Gene Transfer Format");
                info.put("description", "基因注释文件格式，描述基因结构信息");
                info.put("extension", ".gff, .gff3, .gtf");
                info.put("structure", "9列：seqid, source, type, start, end, score, strand, phase, attributes");
                info.put("common_tools", "gffread, AGAT, StringTie, Cufflinks");
                info.put("notes", "GFF3和GTF在attributes列格式上有差异。GTF更常用于RNA-seq分析。1-based坐标。");
            }
            case "BIGWIG", "BW" -> {
                info.put("full_name", "BigWig Format");
                info.put("description", "二进制基因组信号数据格式，用于存储连续的基因组数据（如coverage、信号强度）");
                info.put("extension", ".bigwig, .bw");
                info.put("structure", "二进制索引格式，支持高效的区间查询");
                info.put("common_tools", "UCSC tools, deepTools, IGV, pyBigWig");
                info.put("notes", "由bedGraph转换而来。适合在基因组浏览器中显示信号数据。");
            }
            default -> {
                info.put("full_name", format);
                info.put("description", "未知格式: " + format);
                info.put("notes", "未找到该格式的详细信息。请检查格式名称是否正确。");
            }
        }

        return info;
    }
}
