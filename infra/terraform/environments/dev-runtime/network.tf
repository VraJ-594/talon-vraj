resource "aws_vpc" "app" {
  cidr_block           = "10.42.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = "${local.resource_prefix}-vpc-vraj" }
}

resource "aws_internet_gateway" "app" {
  vpc_id = aws_vpc.app.id

  tags = { Name = "${local.resource_prefix}-igw-vraj" }
}

resource "aws_subnet" "public" {
  count = 2

  vpc_id                  = aws_vpc.app.id
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  cidr_block              = cidrsubnet(aws_vpc.app.cidr_block, 8, count.index)
  map_public_ip_on_launch = true

  tags = { Name = "${local.resource_prefix}-public-${count.index + 1}-vraj" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.app.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.app.id
  }

  tags = { Name = "${local.resource_prefix}-public-rt-vraj" }
}

resource "aws_route_table_association" "public" {
  count = 2

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_security_group" "alb" {
  name        = "${local.resource_prefix}-alb-sg-vraj"
  description = "Restricted HTTP ingress for the Talon demo ALB."
  vpc_id      = aws_vpc.app.id

  ingress {
    description = "Explicit demo reviewer CIDRs"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = var.allowed_demo_cidrs
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${local.resource_prefix}-alb-sg-vraj" }
}

resource "aws_security_group" "task" {
  name        = "${local.resource_prefix}-task-sg-vraj"
  description = "Allows only ALB traffic into the Talon Fargate task."
  vpc_id      = aws_vpc.app.id

  ingress {
    description     = "Web traffic from the ALB"
    from_port       = 80
    to_port         = 80
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${local.resource_prefix}-task-sg-vraj" }
}
